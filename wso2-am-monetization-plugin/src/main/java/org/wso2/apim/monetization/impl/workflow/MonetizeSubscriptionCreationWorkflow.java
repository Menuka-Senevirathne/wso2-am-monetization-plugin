package org.wso2.apim.monetization.impl.workflow;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.moesif.api.MoesifAPIClient;
import com.moesif.api.models.UserBuilder;
import com.moesif.api.models.UserModel;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONObject;
import org.wso2.apim.monetization.impl.MoesifMonetizationConstants;
import org.wso2.apim.monetization.impl.MoesifMonetizationImpl;
import org.wso2.apim.monetization.impl.MonetizationDAO;
import org.wso2.apim.monetization.impl.StripeMonetizationConstants;
import org.wso2.apim.monetization.impl.util.MonetizationUtils;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.WorkflowResponse;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.api.model.Subscriber;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.APIManagerConfiguration;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.dto.SubscriptionWorkflowDTO;
import org.wso2.carbon.apimgt.impl.dto.WorkflowDTO;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.impl.workflow.GeneralWorkflowResponse;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowException;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowExecutor;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowStatus;
import org.wso2.carbon.apimgt.persistence.PersistenceManager;
import org.wso2.carbon.apimgt.persistence.dto.Organization;
import org.wso2.carbon.apimgt.persistence.dto.PublisherAPI;
import org.wso2.carbon.apimgt.persistence.exceptions.APIPersistenceException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class MonetizeSubscriptionCreationWorkflow extends WorkflowExecutor {

    private final MonetizationDAO monetizationDAO = MonetizationDAO.getInstance();

    @Override
    public String getWorkflowType() {
        return null;
    }

    @Override
    public List<WorkflowDTO> getWorkflowDetails(String s) throws WorkflowException {
        return null;
    }

    @Override
    public WorkflowResponse execute(WorkflowDTO workflowDTO) throws WorkflowException {

        SubscriptionWorkflowDTO subsWorkflowDTO = (SubscriptionWorkflowDTO) workflowDTO;
        workflowDTO.setProperties("apiName", subsWorkflowDTO.getApiName());
        workflowDTO.setProperties("apiVersion", subsWorkflowDTO.getApiVersion());
        workflowDTO.setProperties("subscriber", subsWorkflowDTO.getSubscriber());
        workflowDTO.setProperties("applicationName", subsWorkflowDTO.getApplicationName());
        super.execute(workflowDTO);
        workflowDTO.setStatus(WorkflowStatus.APPROVED);
        WorkflowResponse workflowResponse = complete(workflowDTO);

        return workflowResponse;
    }

    @Override
    public WorkflowResponse complete(WorkflowDTO workflowDTO) throws WorkflowException {

        workflowDTO.setUpdatedTime(System.currentTimeMillis());
        super.complete(workflowDTO);
        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
        try {
            apiMgtDAO.updateSubscriptionStatus(Integer.parseInt(workflowDTO.getWorkflowReference()),
                    APIConstants.SubscriptionStatus.UNBLOCKED);
        } catch (APIManagementException e) {
            throw new WorkflowException("Could not complete subscription creation workflow", e);
        }
        return new GeneralWorkflowResponse();
    }

    @Override
    public WorkflowResponse monetizeSubscription(WorkflowDTO workflowDTO, API api) throws WorkflowException {

        SubscriptionWorkflowDTO subWorkFlowDTO;
        Subscriber subscriber;

        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
        subWorkFlowDTO = (SubscriptionWorkflowDTO) workflowDTO;
        //read the platform account key of Stripe
        Stripe.apiKey = MonetizationUtils.getPlatformAccountKey(subWorkFlowDTO.getTenantId());

        //Create customer in Stripe
        CustomerCreateParams params =
                CustomerCreateParams.builder()
                        .setName(((SubscriptionWorkflowDTO) workflowDTO).getSubscriber())
                        .build();
        Customer customer;

        try {
            customer = Customer.create(params);

            //create user in Moesif

//            MoesifAPIClient client = new MoesifAPIClient(Moesif_Application_Key);
//
//            UserModel user = new UserBuilder()
//                    .userId(customer.getName())
//                    .companyId(customer.getId()) // If set, associate user with a company object
//                    .build();
//
//            client.getAPI().updateUser(user);

            String Moesif_Application_Key = MonetizationUtils.getMoesifApplicationKey(workflowDTO.getTenantDomain());
            String apiUrl = "https://api.moesif.com/v1/search/~/users";

            // JSON body
            JsonObject createUserPayload = new JsonObject();
            createUserPayload.addProperty("user_id", customer.getName());
            createUserPayload.addProperty("company_id", customer.getId());
            createUserPayload.addProperty("name", customer.getName());

            MonetizationUtils.sendPostRequest(apiUrl, createUserPayload.toString(), Moesif_Application_Key);


        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }

        String priceId;
        try (Connection con = APIMgtDBUtil.getConnection()) {

            int apiId = ApiMgtDAO.getInstance().getAPIID(api.getUuid(), con);
            priceId = monetizationDAO.getPriceIdForTier(apiId,
                    subWorkFlowDTO.getTierName());
        } catch (APIManagementException | SQLException e) {
            throw new RuntimeException(e);
        }


//        String priceId = "";


        try {
            createMonetizedSubscriptions(priceId, customer);
        } catch (StripeException e) {
            throw new RuntimeException(e);
        }

        return execute(workflowDTO);
    }


//    private String getPlatformAccountKey(int tenantId) throws WorkflowException {
//
//        String stripePlatformAccountKey = null;
//        String tenantDomain = APIUtil.getTenantDomainFromTenantId(tenantId);
//        try {
//            //get the stripe key of platform account from  tenant conf json file
//            JSONObject tenantConfig = APIUtil.getTenantConfig(tenantDomain);
//            if (tenantConfig.containsKey(StripeMonetizationConstants.MONETIZATION_INFO)) {
//                JSONObject monetizationInfo = (JSONObject) tenantConfig
//                        .get(StripeMonetizationConstants.MONETIZATION_INFO);
//                if (monetizationInfo.containsKey(StripeMonetizationConstants.BILLING_ENGINE_PLATFORM_ACCOUNT_KEY)) {
//                    stripePlatformAccountKey = monetizationInfo
//                            .get(StripeMonetizationConstants.BILLING_ENGINE_PLATFORM_ACCOUNT_KEY).toString();
//                    if (StringUtils.isBlank(stripePlatformAccountKey)) {
//                        String errorMessage = "Stripe platform account key is empty for tenant : " + tenantDomain;
//                        throw new WorkflowException(errorMessage);
//                    }
//                    return stripePlatformAccountKey;
//                }
//            }
//        } catch (APIManagementException e) {
//            throw new WorkflowException("Failed to get the configuration for tenant from DB:  " + tenantDomain, e);
//        }
//
//        return stripePlatformAccountKey;
//    }

    public void createMonetizedSubscriptions(String priceId,
                                             Customer customer) throws WorkflowException, StripeException {
        try {
            SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                    .setCustomer(customer.getId())
                    .addItem(SubscriptionCreateParams.Item.builder()
                            .setPrice(priceId)
                            .build()
                    ).build();
            Subscription subscription = Subscription.create(params);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    //create billing meter

    //assign billing meter to a company

}
