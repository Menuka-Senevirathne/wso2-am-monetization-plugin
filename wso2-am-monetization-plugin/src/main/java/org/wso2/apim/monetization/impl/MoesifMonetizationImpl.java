package org.wso2.apim.monetization.impl;

import com.google.gson.JsonParser;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Invoice;
import com.stripe.param.InvoiceCreatePreviewParams;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.apim.monetization.impl.constants.MoesifMonetizationConstants;
import org.wso2.apim.monetization.impl.enums.MoesifPricingModel;
import org.wso2.apim.monetization.impl.enums.Provider;
import org.wso2.apim.monetization.impl.model.MonetizedStripeSubscriptionInfo;
import org.wso2.apim.monetization.impl.util.MonetizationUtils;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.APIProvider;
import org.wso2.carbon.apimgt.api.MonetizationException;
import org.wso2.carbon.apimgt.api.model.*;
import org.wso2.carbon.apimgt.api.model.policy.SubscriptionPolicy;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import com.google.gson.JsonObject;
import com.google.gson.Gson;

import org.wso2.carbon.apimgt.impl.workflow.WorkflowException;
import org.wso2.carbon.context.PrivilegedCarbonContext;

import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;


public class MoesifMonetizationImpl implements Monetization {
    private static final Log log = LogFactory.getLog(MoesifMonetizationImpl.class);

    private final MonetizationDAO monetizationDAO = MonetizationDAO.getInstance();

    @Override
    public boolean createBillingPlan(SubscriptionPolicy subscriptionPolicy) throws MonetizationException {
        //Billing plan creation for Moesif has moved to the enableMonetization method
        return false;
    }

    @Override
    public boolean updateBillingPlan(SubscriptionPolicy subscriptionPolicy) throws MonetizationException {
        return false;
    }

    @Override
    public boolean deleteBillingPlan(SubscriptionPolicy subscriptionPolicy) throws MonetizationException {
        return false;
    }

    @Override
    public boolean enableMonetization(String tenantDomain, API api, Map<String, String> monetizationProperties) throws MonetizationException {
        // Retrieve Moesif application key
        String moesifApplicationKey = MonetizationUtils.getMoesifApplicationKey(tenantDomain);

        try {
            String apiName = api.getId().getApiName();
            String apiVersion = api.getId().getVersion();
            String apiProvider = api.getId().getProviderName();
            String moesifPlanName = apiName + "-" + apiVersion + "-" + apiProvider;

            //creating a plan in Moesif which resembles the API in APIM
            String moesifPlanResponse = createMoesifPlan(api, moesifApplicationKey, moesifPlanName);

            String planId = extractId(moesifPlanResponse);

            if (StringUtils.isNotBlank(planId)) {

                Map<String, String> tierPlanMap = new HashMap<String, String>();
                //scan for commercial tiers and add price to the above created plan
                for (Tier currentTier : api.getAvailableTiers()) {
                    if (APIConstants.COMMERCIAL_TIER_PLAN.equalsIgnoreCase(currentTier.getTierPlan())) {
                        if (StringUtils.isNotBlank(planId)) {
                            log.info("Current Tier " + currentTier);

                            //Create a price in Moesif for commercial tier
                            String priceResponse = createMoesifPrice(currentTier, planId, moesifApplicationKey, moesifPlanName);
                            log.info("Price Response: " + priceResponse);

                            if (StringUtils.isNotBlank(priceResponse)) {
                                String priceId = extractId(priceResponse);
                                if (StringUtils.isNotBlank(priceId)) {
                                    tierPlanMap.put(currentTier.getName(), priceId);
                                    log.info("Monetization is enabled for the API: " + api.getId() +
                                            " with Tier: " + currentTier.getName() + " and Moesif Price ID: " + priceId);
                                } else {
                                    String errorMessage = "Failed to extract price_id from Moesif response";
                                    throw new MonetizationException(errorMessage);
                                }
                            }
                            try (Connection con = APIMgtDBUtil.getConnection()) {
                                int apiId = ApiMgtDAO.getInstance().getAPIID(api.getUuid(), con);
                                monetizationDAO.addMonetizationData(apiId, planId, moesifPlanName, tierPlanMap);
                            } catch (Exception e) {
                                String errorMessage = String.format(
                                        "Failed to persist monetization data for API [uuid: %s, planId: %s]",
                                        api.getUuid(), planId);
                                log.error(errorMessage, e);
                                throw new MoesifMonetizationException(errorMessage, e);
                            }

                        }
                    }
                }
            } else {
                throw new MonetizationException("Plan ID " + planId + " not yet available in Moesif");
            }

        } catch (MoesifMonetizationException e) {
            String errorMessage = "Failed to get Moesif account key for tenant :  " +
                    tenantDomain;
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }

        return true;
    }

    @Override
    public boolean disableMonetization(String s, API api, Map<String, String> map) throws MonetizationException {
        return false;
    }

    @Override
    public Map<String, String> getMonetizedPoliciesToPlanMapping(API api) throws MonetizationException {

        try (Connection con = APIMgtDBUtil.getConnection()) {
            int apiId = ApiMgtDAO.getInstance().getAPIID(api.getUuid(), con);
            return MonetizationDAO.getTierToBillingPlanMapping(apiId);
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get ID from database for : " + api.getId().getApiName() +
                    " when getting tier to billing engine plan mapping.";
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        } catch (SQLException e) {
            String errorMessage = "Error while retrieving the API ID";
            throw new MonetizationException(errorMessage, e);
        }
    }


    @Override
    public Map<String, String> getCurrentUsageForSubscription(String subscriptionUUID, APIProvider apiProvider) throws MonetizationException {

        Map<String, String> billingEngineUsageData = new HashMap<String, String>();
        String apiName = null;
        try (Connection con = APIMgtDBUtil.getConnection()) {
            SubscribedAPI subscribedAPI = ApiMgtDAO.getInstance().getSubscriptionByUUID(subscriptionUUID);
            APIIdentifier apiIdentifier = subscribedAPI.getAPIIdentifier();
            APIProductIdentifier apiProductIdentifier;
            API api;
            APIProduct apiProduct;
            HashMap monetizationDataMap;
            int apiId;
            if (apiIdentifier != null) {
                api = apiProvider.getAPIbyUUID(apiIdentifier.getUUID(), apiIdentifier.getOrganization());
                apiName = apiIdentifier.getApiName();
                if (api.getMonetizationProperties() == null) {
                    String errorMessage = "Monetization properties are empty for : " + apiName;
                    //throw MonetizationException as it will be logged and handled by the caller
                    throw new MonetizationException(errorMessage);
                }
                monetizationDataMap = new Gson().fromJson(api.getMonetizationProperties().toString(), HashMap.class);
                if (MapUtils.isEmpty(monetizationDataMap)) {
                    String errorMessage = "Monetization data map is empty for : " + apiName;
                    //throw MonetizationException as it will be logged and handled by the caller
                    throw new MonetizationException(errorMessage);
                }
                apiId = ApiMgtDAO.getInstance().getAPIID(api.getUuid(), con);
            } else {
                apiProductIdentifier = subscribedAPI.getProductId();
                apiProduct = apiProvider.getAPIProduct(apiProductIdentifier);
                apiName = apiProductIdentifier.getName();
                if (apiProduct.getMonetizationProperties() == null) {
                    String errorMessage = "Monetization properties are empty for : " + apiName;
                    //throw MonetizationException as it will be logged and handled by the caller
                    throw new MonetizationException(errorMessage);
                }
                monetizationDataMap = new Gson().fromJson(apiProduct.getMonetizationProperties().toString(),
                        HashMap.class);
                if (MapUtils.isEmpty(monetizationDataMap)) {
                    String errorMessage = "Monetization data map is empty for : " + apiName;
                    //throw MonetizationException as it will be logged and handled by the caller
                    throw new MonetizationException(errorMessage);
                }
                apiId = ApiMgtDAO.getInstance().getAPIProductId(apiProductIdentifier);
            }

            String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
            int applicationId = subscribedAPI.getApplication().getId();

            MonetizedStripeSubscriptionInfo monetizedStripeSubscriptionInfo =
                    monetizationDAO.getMonetizedSubscription(apiId, applicationId);

            Stripe.apiKey = MonetizationUtils.getPlatformAccountKey(tenantDomain);
            InvoiceCreatePreviewParams params =
                    InvoiceCreatePreviewParams.builder()
                            .setCustomer(monetizedStripeSubscriptionInfo.getCustomerId())
                            .setSubscription(monetizedStripeSubscriptionInfo.getSubscriptionId())
                            .build();

            Invoice invoice = Invoice.createPreview(params);


            if (invoice == null) {
                String errorMessage = "No billing engine subscription was found for : " + apiName;
                //throw MonetizationException as it will be logged and handled by the caller
                throw new MonetizationException(errorMessage);
            }
            SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
            dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            //the below parameters are billing engine specific
            billingEngineUsageData.put("Description", invoice.getDescription());
            billingEngineUsageData.put("Paid", invoice.getAmountPaid() != null ? invoice.getAmountPaid().toString() : null);
//            billingEngineUsageData.put("Tax", invoice.getTa() != null ?
//                    invoice.getTax().toString() : null);
            billingEngineUsageData.put("Invoice ID", invoice.getId());
            billingEngineUsageData.put("Account Name", invoice.getAccountName());
            billingEngineUsageData.put("Next Payment Attempt", invoice.getNextPaymentAttempt() != null ?
                    dateFormatter.format(new Date(invoice.getNextPaymentAttempt() * 1000)) : null);
            billingEngineUsageData.put("Customer Email", invoice.getCustomerEmail());
            billingEngineUsageData.put("Currency", invoice.getCurrency());
            billingEngineUsageData.put("Account Country", invoice.getAccountCountry());
            billingEngineUsageData.put("Amount Remaining", invoice.getAmountRemaining() != null ?
                    Long.toString(invoice.getAmountRemaining() / 100L) : null);
            billingEngineUsageData.put("Period End", invoice.getPeriodEnd() != null ?
                    dateFormatter.format(new Date(invoice.getPeriodEnd() * 1000)) : null);
            billingEngineUsageData.put("Due Date", invoice.getDueDate() != null ?
                    dateFormatter.format(new Date(invoice.getDueDate())) : null);
            billingEngineUsageData.put("Amount Due", invoice.getAmountDue() != null ?
                    Long.toString(invoice.getAmountDue() / 100L) : null);
            billingEngineUsageData.put("Total Tax Amounts", invoice.getTotalTaxes() != null ?
                    invoice.getTotalTaxes().toString() : null);
            billingEngineUsageData.put("Amount Paid", invoice.getAmountPaid() != null ?
                    Long.toString(invoice.getAmountPaid() / 100L) : null);
            billingEngineUsageData.put("Subtotal", invoice.getSubtotal() != null ?
                    Long.toString(invoice.getSubtotal() / 100L) : null);
            billingEngineUsageData.put("Total", invoice.getTotal() != null ?
                    Long.toString(invoice.getTotal() / 100L) : null);
            billingEngineUsageData.put("Period Start", invoice.getPeriodStart() != null ?
                    dateFormatter.format(new Date(invoice.getPeriodStart() * 1000)) : null);


        } catch (StripeException e) {
            String errorMessage = "Error while fetching billing engine usage data for : " + apiName;
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get subscription details of : " + apiName;
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        } catch (SQLException e) {
            String errorMessage = "Error while retrieving the API ID";
            throw new MonetizationException(errorMessage, e);
        } catch (WorkflowException e) {
            throw new RuntimeException(e);
        }
        return billingEngineUsageData;
    }

    @Override
    public Map<String, String> getTotalRevenue(API api, APIProvider apiProvider) throws MonetizationException {
        APIIdentifier apiIdentifier = api.getId();
        Map<String, String> revenueData = new HashMap<String, String>();
        try {
            //get all subscriptions for the API
            List<SubscribedAPI> apiUsages = apiProvider.getAPIUsageByAPIId(api.getUuid(),
                    api.getId().getOrganization());
            for (SubscribedAPI subscribedAPI : apiUsages) {
                //get subscription UUID for each subscription
                int subscriptionId = subscribedAPI.getSubscriptionId();
                String subscriptionUUID = monetizationDAO.getSubscriptionUUID(subscriptionId);
//                String subscriptionUUID = "26135517-d598-4739-abf2-2384e2935611";
                //get revenue for each subscription and add them
                Map<String, String> billingEngineUsageData = getCurrentUsageForSubscription(subscriptionUUID,
                        apiProvider);
                revenueData.put("Revenue for subscription ID : " + subscriptionId,
                        billingEngineUsageData.get("amount_due"));
            }
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get subscriptions of : " + apiIdentifier.getApiName();
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
        return revenueData;
    }

    @Override
    public boolean publishMonetizationUsageRecords(MonetizationUsagePublishInfo monetizationUsagePublishInfo) throws MonetizationException {
        return false;
    }

    /**
     * Extracts the "id" field from a JSON response string.
     *
     * @param jsonResponse The JSON response string from which to extract the ID.
     * @return The extracted ID as a String, or null if not found.
     */
    private String extractId(String jsonResponse) {
        JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
        if (jsonObject.has("id") && !jsonObject.get("id").isJsonNull()) {
            return jsonObject.get("id").getAsString();
        }
        return null;
    }

    /**
     * Creates a billing plan in Moesif for the given API.
     *
     * @param api The API object for which the billing plan should be created.
     * @return The response from Moesif API after creating the billing plan.
     * @throws MoesifMonetizationException if any error occurs while creating the plan.
     */
    private String createMoesifPlan(API api, String moesifApplicationKey, String moesifPlanName) throws MoesifMonetizationException {

        String createBillingPlanURL = null;
        String planResponse = null;

        try {

            // Construct billing plan URL for provider (Stripe in this case)
            createBillingPlanURL = MonetizationUtils.constructProviderURL(
                    MoesifMonetizationConstants.BILLING_PLANS_URL, org.wso2.apim.monetization.impl.enums.Provider.STRIPE);

            // Prepare JSON payload
            JsonObject createPlanPayload = new JsonObject();
            createPlanPayload.addProperty("name", moesifPlanName);
            createPlanPayload.addProperty("status", MoesifMonetizationConstants.BILLING_PLAN_STATUS_ACTIVE);
            createPlanPayload.addProperty("provider", org.wso2.apim.monetization.impl.enums.Provider.STRIPE.getValue());

            planResponse = MonetizationUtils.invokeService(createBillingPlanURL, createPlanPayload.toString(),
                    moesifApplicationKey);

            if (log.isDebugEnabled()) {
                log.debug("Plan creation payload: " + createPlanPayload);
            }
            log.info("Plan created successfully in Moesif: " + moesifPlanName);

        } catch (Exception e) {
            String errorMessage = String.format(
                    "Error while creating Moesif billing plan for API [name: %s, version: %s, provider: %s]",
                    api.getId().getApiName(), api.getId().getVersion(), api.getId().getProviderName());
            log.error(errorMessage, e);
            throw new MoesifMonetizationException(errorMessage, e);
        }

        return planResponse;
    }


    /**
     * Creates a billing price in Moesif for a given plan and tier.
     *
     * @param currentTier          The monetization tier details.
     * @param planId               The Moesif plan ID under which this price should be created.
     * @param moesifApplicationKey The Moesif application key for authentication.
     * @param moesifPlanName       The associated Moesif plan name (used for meter naming).
     * @return The response from Moesif API after creating the billing price.
     * @throws MoesifMonetizationException if any error occurs while creating the price.
     */
    private String createMoesifPrice(Tier currentTier, String planId,
                                     String moesifApplicationKey, String moesifPlanName)
            throws MoesifMonetizationException {
        String createPriceUrl;
        String priceResponse;

        try {
            // Construct billing price URL for Stripe
            createPriceUrl = MonetizationUtils.constructProviderURL(
                    MoesifMonetizationConstants.BILLING_PRICE_URL, org.wso2.apim.monetization.impl.enums.Provider.STRIPE);

            // Build price creation payload
            JsonObject createPricePayload = new JsonObject();
            createPricePayload.addProperty("name", currentTier.getName());
            createPricePayload.addProperty("provider", Provider.STRIPE.getValue());
            createPricePayload.addProperty("plan_id", planId);
            createPricePayload.addProperty("status", MoesifMonetizationConstants.BILLING_PRICE_STATUS_ACTIVE);

            // Decide pricing model based on tier attributes
            String pricePerRequest = currentTier.getMonetizationAttributes().get("pricePerRequest");
            String fixedPrice = currentTier.getMonetizationAttributes().get("fixedPrice");

            if (pricePerRequest != null && !pricePerRequest.isEmpty()) {
                // Per-unit (metered) pricing model
                createPricePayload.addProperty("pricing_model", MoesifPricingModel.PER_UNIT.getValue());
                createPricePayload.addProperty("price_in_decimal", pricePerRequest);

                // Attach billing meter
                JsonObject billingMeterPayload = new JsonObject();
                billingMeterPayload.addProperty("display_name", currentTier.getName() + " Meter");
                billingMeterPayload.addProperty("event_name", moesifPlanName + " Event");
                createPricePayload.add("price_meter", billingMeterPayload);

            //Todo: The floe for the fixed price tier should be implemented and tested
            } else if (fixedPrice != null && !fixedPrice.isEmpty()) {
                // Flat-rate pricing model
                createPricePayload.addProperty("pricing_model", MoesifPricingModel.FLAT_RATE.getValue());
                createPricePayload.addProperty("price_in_decimal", fixedPrice);

                // Placeholder for governance rule attachment
                createPricePayload.addProperty("usage_aggregator", "");
            }

            //Todo: period and period_units should be handled dynamically,
            // the possible set of values are not available in the Moesif openAPI spec
            createPricePayload.addProperty("period", 1);
            createPricePayload.addProperty("period_units", "M");
            createPricePayload.addProperty("currency",
                    currentTier.getMonetizationAttributes().get("currencyType"));

            priceResponse = MonetizationUtils.invokeService(
                    createPriceUrl, createPricePayload.toString(), moesifApplicationKey);

            if (log.isDebugEnabled()) {
                log.debug("Price creation payload: " + createPricePayload);
                log.debug("Price creation response: " + priceResponse);
            }
            log.info("Moesif billing price created successfully for tier: " + currentTier.getName());

        } catch (Exception e) {
            String errorMessage = String.format(
                    "Error while creating Moesif billing price for tier [%s] under plan [%s]",
                    currentTier.getName(), planId);
            log.error(errorMessage, e);
            throw new MoesifMonetizationException(errorMessage, e);
        }

        return priceResponse;
    }
}
