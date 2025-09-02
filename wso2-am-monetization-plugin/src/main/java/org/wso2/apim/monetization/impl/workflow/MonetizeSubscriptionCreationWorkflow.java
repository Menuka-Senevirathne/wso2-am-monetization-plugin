package org.wso2.apim.monetization.impl.workflow;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONObject;
import org.wso2.apim.monetization.impl.*;
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

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class MonetizeSubscriptionCreationWorkflow extends WorkflowExecutor {

    private static final Log log = LogFactory.getLog(MonetizeSubscriptionCreationWorkflow.class);
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
//
//    @Override
//    public WorkflowResponse monetizeSubscription(WorkflowDTO workflowDTO, API api) throws WorkflowException {
//
//        SubscriptionWorkflowDTO subWorkFlowDTO;
//        Subscriber subscriber;
//        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
//        subWorkFlowDTO = (SubscriptionWorkflowDTO) workflowDTO;
//        //read the platform account key of Stripe
//        Stripe.apiKey = MonetizationUtils.getPlatformAccountKey(subWorkFlowDTO.getTenantId());
////
////        //Create customer in Stripe
////        CustomerCreateParams params =
////                CustomerCreateParams.builder()
////                        .setName(((SubscriptionWorkflowDTO) workflowDTO).getSubscriber())
////                        .build();
//        Customer customer;
//        String Moesif_Application_Key = null;
//        try {
//            Moesif_Application_Key = MonetizationUtils.getMoesifApplicationKey(workflowDTO.getTenantDomain());
//        } catch (MoesifMonetizationException e) {
//            throw new RuntimeException(e);
//        }
//        try {
////            customer = Customer.create(params);
//            customer = createStripeCustomer(workflowDTO);
//            //create user in Moesif
//
////            MoesifAPIClient client = new MoesifAPIClient(Moesif_Application_Key);
////
////            UserModel user = new UserBuilder()
////                    .userId(customer.getName())
////                    .companyId(customer.getId()) // If set, associate user with a company object
////                    .build();
////
////            client.getAPI().updateUser(user);
//
////            String Moesif_Application_Key = MonetizationUtils.getMoesifApplicationKey(workflowDTO.getTenantDomain());
////            String apiUrl = "https://api.moesif.com/v1/search/~/users";
////
////            // JSON body
////            JsonObject createUserPayload = new JsonObject();
////            createUserPayload.addProperty("user_id", customer.getName());
////            createUserPayload.addProperty("company_id", customer.getId());
////            createUserPayload.addProperty("name", customer.getName());
////
////            MonetizationUtils.sendPostRequest(apiUrl, createUserPayload.toString(), Moesif_Application_Key);
//
//
//        } catch (Throwable ex) {
//            throw new RuntimeException(ex);
//        }
//
//        try {
//            createUserInMoesif(customer, Moesif_Application_Key);
//        } catch (MoesifMonetizationException e) {
//            throw new RuntimeException(e);
//        }
//
//        String priceId;
//        MoesifPlanInfo moesifPlanInfo;
//        try (Connection con = APIMgtDBUtil.getConnection()) {
//
//            int apiId = ApiMgtDAO.getInstance().getAPIID(api.getUuid(), con);
////            priceId = monetizationDAO.getPriceIdForTier(apiId,
////                    subWorkFlowDTO.getTierName());
//            moesifPlanInfo = monetizationDAO.getPlanInfoForTier(apiId,
//                    subWorkFlowDTO.getTierName());
//            priceId = moesifPlanInfo.getPriceId();
//        } catch (APIManagementException | SQLException e) {
//            throw new RuntimeException(e);
//        }
//
//
//        String subscriptionId;
//        try {
//            subscriptionId = createMonetizedSubscriptions(priceId, customer);
//        } catch (StripeException e) {
//            throw new RuntimeException(e);
//        }
//
//        //Create billing meter in Moesif
//        try {
//            createBillingMeterInMoesif(subscriptionId, moesifPlanInfo, Moesif_Application_Key);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        return execute(workflowDTO);
//    }

    /**
     * Handles subscription monetization workflow.
     *
     * @param workflowDTO Workflow details
     * @param api         API for which monetization is enabled
     * @return WorkflowResponse indicating success/failure
     * @throws WorkflowException if monetization setup fails
     */
    @Override
    public WorkflowResponse monetizeSubscription(WorkflowDTO workflowDTO, API api) throws WorkflowException {

        SubscriptionWorkflowDTO subWorkFlowDTO = (SubscriptionWorkflowDTO) workflowDTO;
        String tenantDomain = workflowDTO.getTenantDomain();
        APIIdentifier identifier = new APIIdentifier(subWorkFlowDTO.getApiProvider(), subWorkFlowDTO.getApiName(),
                subWorkFlowDTO.getApiVersion());

        Customer customer;
        MoesifPlanInfo moesifPlanInfo;
        String moesifApplicationKey;
        String priceId;
        String subscriptionId;

        try {
            // 1. Initialize Stripe API key for this tenant
            Stripe.apiKey = MonetizationUtils.getPlatformAccountKey(tenantDomain);

            // 2. Get Moesif application key
            moesifApplicationKey = MonetizationUtils.getMoesifApplicationKey(tenantDomain);

            // 3. Create customer in Stripe
            customer = createStripeCustomer(workflowDTO);
            log.info("Created Stripe customer [id: " + customer.getId() + ", name: " + customer.getName() + "]");

            // 4. Register user in Moesif
            createUserInMoesif(customer, moesifApplicationKey);
            log.info("Created Moesif user for Stripe customer [id: " + customer.getId() + "]");

            // 5. Fetch monetization plan info from DB
            try (Connection con = APIMgtDBUtil.getConnection()) {
                int apiId = ApiMgtDAO.getInstance().getAPIID(api.getUuid(), con);
                moesifPlanInfo = monetizationDAO.getPlanInfoForTier(apiId, subWorkFlowDTO.getTierName());
                priceId = moesifPlanInfo.getPriceId();
            }

            // 6. Create subscription in Stripe
            subscriptionId = createMonetizedSubscriptions(priceId, customer);
            log.info("Created Stripe subscription [id: " + subscriptionId + "] for price [id: " + priceId + "]");

            monetizationDAO.addSubscription(identifier, subWorkFlowDTO.getApplicationId(),subWorkFlowDTO.getTenantId(),
                    customer.getId(), subscriptionId, api.getUuid());

            // 7. Create billing meter in Moesif
            createBillingMeterInMoesif(subscriptionId, moesifPlanInfo, moesifApplicationKey);
            log.info("Created Moesif billing meter for subscription [id: " + subscriptionId + "]");

        } catch (StripeException e) {
            String msg = "Error while interacting with Stripe during subscription monetization";
            log.error(msg, e);
            throw new WorkflowException(msg, e);

        } catch (SQLException | APIManagementException e) {
            String msg = "Error while accessing API management DB during subscription monetization";
            log.error(msg, e);
            throw new WorkflowException(msg, e);

        } catch (IOException e) {
            String msg = "Error while creating billing meter in Moesif";
            log.error(msg, e);
            throw new WorkflowException(msg, e);

        } catch (MoesifMonetizationException e) {
            String msg = "Error while interacting with Moesif during subscription monetization";
            log.error(msg, e);
            throw new WorkflowException(msg, e);

        } catch (Exception e) {
            String msg = "Unexpected error during subscription monetization";
            log.error(msg, e);
            throw new WorkflowException(msg, e);
        }

        // 8. Continue workflow execution
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


    /**
     * Creates a Stripe customer for the given subscription workflow.
     *
     * @param workflowDTO The subscription workflow DTO containing subscriber details.
     * @return The created Stripe Customer object.
     * @throws MoesifMonetizationException if customer creation fails.
     */
    private Customer createStripeCustomer(WorkflowDTO workflowDTO) throws MoesifMonetizationException, StripeMonetizationException {
        try {
            // Build customer creation parameters
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setName(((SubscriptionWorkflowDTO) workflowDTO).getSubscriber())
                    .build();

            // Create customer in Stripe
            Customer customer = Customer.create(params);

            if (log.isDebugEnabled()) {
                log.debug("Stripe customer creation request: " + params);
                log.debug("Stripe customer creation response: " + customer);
            }
            log.info("Stripe customer created successfully for subscriber: "
                    + ((SubscriptionWorkflowDTO) workflowDTO).getSubscriber());

            return customer;

        } catch (Exception e) {
            String errorMessage = String.format(
                    "Error while creating Stripe customer for subscriber [%s]",
                    ((SubscriptionWorkflowDTO) workflowDTO).getSubscriber());
            log.error(errorMessage, e);
            throw new StripeMonetizationException(errorMessage, e);
        }
    }

    /**
     * Creates a user in Moesif based on the given Stripe customer.
     *
     * @param customer             The Stripe customer object.
     * @param moesifApplicationKey The Moesif application key for authentication.
     * @throws MoesifMonetizationException if user creation fails.
     */
    private void createUserInMoesif(Customer customer, String moesifApplicationKey)
            throws MoesifMonetizationException {

        String apiUrl = "https://api.moesif.com/v1/search/~/users";

        try {
            // Build user creation payload
            JsonObject createUserPayload = new JsonObject();
            createUserPayload.addProperty("user_id", customer.getName());   // external-facing ID
            createUserPayload.addProperty("company_id", customer.getId());  // Stripe customer ID
            createUserPayload.addProperty("name", customer.getName());

            // Send request to Moesif
            String response = MonetizationUtils.sendPostRequest(
                    apiUrl, createUserPayload.toString(), moesifApplicationKey);

            if (log.isDebugEnabled()) {
                log.debug("Moesif user creation payload: " + createUserPayload);
                log.debug("Moesif user creation response: " + response);
            }
            log.info("Moesif user created successfully for customer: " + customer.getName());

        } catch (Exception e) {
            String errorMessage = String.format(
                    "Error while creating Moesif user for Stripe customer [id: %s, name: %s]",
                    customer.getId(), customer.getName());
            log.error(errorMessage, e);
            throw new MoesifMonetizationException(errorMessage, e);
        }
    }


    public String createMonetizedSubscriptions(String priceId,
                                               Customer customer) throws WorkflowException, StripeException {
        try {
            SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                    .setCustomer(customer.getId())
                    .addItem(SubscriptionCreateParams.Item.builder()
                            .setPrice(priceId)
                            .build()
                    ).build();
            Subscription subscription = Subscription.create(params);

            return subscription.getId();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    //create billing meter
    public void createBillingMeterInMoesif(String subscriptionId, MoesifPlanInfo moesifPlanInfo, String key) throws IOException {
        JsonObject createBillingMeterPayload = new JsonObject();

        // Root level
        createBillingMeterPayload.addProperty("name", subscriptionId + "-billing-meter");
        createBillingMeterPayload.addProperty("slug", "hourly_usage");
        createBillingMeterPayload.addProperty("status", MoesifMonetizationConstants.BILLING_METER_STATUS_ACTIVE);

        createBillingMeterPayload.addProperty(
                "url_query",
                "?seg[metrics][0][metricName]=sum%28events%29&seg[groups][0][field]=company_id.raw&seg[groups][0][func]=terms&seg[groups][0][buckets]=25&seg[chartType]=table"
        );

//        String payload = "{\n" +
//                "    \"query\": {\n" +
//                "        \"bool\": {\n" +
//                "            \"must\": [\n" +
//                "                {\n" +
//                "                    \"bool\": {\n" +
//                "                        \"should\": [\n" +
//                "                            {\n" +
//                "                                \"terms\": {\n" +
//                "                                    \"subscription_id.raw\": [\n" +
//                "                                        \"12345\"\n" +
//                "                                    ]\n" +
//                "                                }\n" +
//                "                            },\n" +
//                "                            {\n" +
//                "                                \"bool\": {\n" +
//                "                                    \"must_not\": {\n" +
//                "                                        \"exists\": {\n" +
//                "                                            \"field\": \"subscription_id.raw\"\n" +
//                "                                        }\n" +
//                "                                    }\n" +
//                "                                }\n" +
//                "                            }\n" +
//                "                        ]\n" +
//                "                    }\n" +
//                "                },\n" +
//                "                {\n" +
//                "                    \"terms\": {\n" +
//                "                        \"company_id.raw\": [\n" +
//                "                            \"67890\"\n" +
//                "                        ]\n" +
//                "                    }\n" +
//                "                }\n" +
//                "            ]\n" +
//                "        }\n" +
//                "    },\n" +
//                "    \"aggs\": {\n" +
//                "        \"seg\": {\n" +
//                "            \"filter\": {\n" +
//                "                \"match_all\": {}\n" +
//                "            },\n" +
//                "            \"aggs\": {\n" +
//                "                \"usage_value\": {\n" +
//                "                    \"sum\": {\n" +
//                "                        \"field\": \"weight\",\n" +
//                "                        \"missing\": 1\n" +
//                "                    }\n" +
//                "                }\n" +
//                "            }\n" +
//                "        }\n" +
//                "    },\n" +
//                "    \"size\": 0\n" +
//                "}";
//        createBillingMeterPayload.addProperty("es_query", payload);
//        createBillingMeterPayload.addProperty(
//                "es_query",
//                "eyJxdWVyeSI6eyJib29sIjp7Im11c3QiOlt7ImJvb2wiOnsic2hvdWxkIjpbeyJ0ZXJtcyI6eyJzdWJzY3JpcHRpb25faWQucmF3IjpbInt7c3Vic2NyaXB0aW9uX2lkfX0iXX19LHsiYm9vbCI6eyJtdXN0X25vdCI6eyJleGlzdHMiOnsiZmllbGQiOiJzdWJzY3JpcHRpb25faWQucmF3In19fX1dfX0seyJ0ZXJtcyI6eyJjb21wYW55X2lkLnJhdyI6WyJ7e2NvbXBhbnlfaWR9fSJdfX1dfX0sImFnZ3MiOnsic2VnIjp7ImZpbHRlciI6eyJtYXRjaF9hbGwiOnt9fSwiYWdncyI6eyJ1c2FnZV92YWx1ZSI6eyJzdW0iOnsiZmllbGQiOiJ3ZWlnaHQiLCJtaXNzaW5nIjoxfX19fX0sInNpemUiOjB9"
//        );


        JsonObject payload = new JsonObject();

// ===== query =====
        JsonObject query = new JsonObject();
        JsonObject bool = new JsonObject();
        JsonArray mustArray = new JsonArray();

// --- first must -> bool should ---
        JsonObject must1 = new JsonObject();
        JsonObject innerBool = new JsonObject();
        JsonArray shouldArray = new JsonArray();

// should -> terms subscription_id.raw
        JsonObject should1 = new JsonObject();
        JsonObject terms1 = new JsonObject();
        JsonArray subIds = new JsonArray();
        subIds.add("{{subscription_id}}");
        terms1.add("subscription_id.raw", subIds);
        should1.add("terms", terms1);
        shouldArray.add(should1);

// should -> bool must_not exists subscription_id.raw
        JsonObject should2 = new JsonObject();
        JsonObject bool2 = new JsonObject();
        JsonObject mustNot = new JsonObject();
        JsonObject exists = new JsonObject();
        exists.addProperty("field", "subscription_id.raw");
        mustNot.add("exists", exists);
        bool2.add("must_not", mustNot);
        should2.add("bool", bool2);
        shouldArray.add(should2);

// put should[] inside bool
        innerBool.add("should", shouldArray);
        must1.add("bool", innerBool);
        mustArray.add(must1);

// --- second must -> terms company_id.raw ---
        JsonObject must2 = new JsonObject();
        JsonObject terms2 = new JsonObject();
        JsonArray companyIds = new JsonArray();
        companyIds.add("{{company_id}}");
        terms2.add("company_id.raw", companyIds);
        must2.add("terms", terms2);
        mustArray.add(must2);

// build bool.must
        bool.add("must", mustArray);
        query.add("bool", bool);
        payload.add("query", query);

// ===== aggs =====
        JsonObject aggs = new JsonObject();
        JsonObject seg = new JsonObject();

// filter: match_all
        JsonObject filter = new JsonObject();
        filter.add("match_all", new JsonObject());
        seg.add("filter", filter);

// aggs inside seg
        JsonObject segAggs = new JsonObject();
        JsonObject usageValue = new JsonObject();
        JsonObject sum = new JsonObject();
        sum.addProperty("field", "weight");
        sum.addProperty("missing", 1);
        usageValue.add("sum", sum);
        segAggs.add("usage_value", usageValue);
        seg.add("aggs", segAggs);

// add seg under aggs
        aggs.add("seg", seg);
        payload.add("aggs", aggs);

// ===== size =====
        payload.addProperty("size", 0);

// ===== final =====
        System.out.println(payload.toString());

// attach to your createBillingMeterPayload
        createBillingMeterPayload.add("es_query", payload);


        // billing_plan
        JsonObject billingPlan = new JsonObject();
        billingPlan.addProperty("provider_slug", Provider.STRIPE.getValue());

        JsonObject params = new JsonObject();

        // stripe_params
        JsonObject stripeParams = new JsonObject();

        // product
        JsonObject product = new JsonObject();
        product.addProperty("name", moesifPlanInfo.getPlanName());
        product.addProperty("id", moesifPlanInfo.getPlanId());
        stripeParams.add("product", product);

        // prices
        JsonArray pricesArray = new JsonArray();
        JsonObject price = new JsonObject();
        price.addProperty("price_id", moesifPlanInfo.getPriceId());
        pricesArray.add(price);
        stripeParams.add("prices", pricesArray);

        // reporting
        JsonObject reporting = new JsonObject();
        reporting.addProperty("reporting_period", "5m");
        stripeParams.add("reporting", reporting);

        // add stripe_params
        params.add("stripe_params", stripeParams);
        params.addProperty("usage_multiplier", 1);
        params.addProperty("usage_rounding_mode", "up");

        // add params to billing_plan
        billingPlan.add("params", params);

        // add billing_plan to root
        createBillingMeterPayload.add("billing_plan", billingPlan);

        if (log.isDebugEnabled()) {
            log.debug("Moesif billing meter creation payload: " + createBillingMeterPayload);
        }

        MonetizationUtils.sendPostRequest("https://api.moesif.com/v1/~/billing/meters", createBillingMeterPayload.toString(), key);

    }


    //assign billing meter to a company

}
