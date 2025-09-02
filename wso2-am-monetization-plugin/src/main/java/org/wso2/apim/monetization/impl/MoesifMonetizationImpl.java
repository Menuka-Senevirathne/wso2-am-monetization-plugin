package org.wso2.apim.monetization.impl;

import com.moesif.api.MoesifAPIClient;
import com.stripe.Stripe;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONObject;
import org.wso2.apim.monetization.impl.util.MonetizationUtils;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.APIProvider;
import org.wso2.carbon.apimgt.api.MonetizationException;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.Monetization;
import org.wso2.carbon.apimgt.api.model.MonetizationUsagePublishInfo;
import org.wso2.carbon.apimgt.api.model.Tier;
import org.wso2.carbon.apimgt.api.model.policy.SubscriptionPolicy;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import com.google.gson.JsonObject;
import com.google.gson.Gson;

import com.moesif.*;
import org.wso2.carbon.context.PrivilegedCarbonContext;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;


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

        //Create product resembles the API

        try {
            //read tenant conf and get application account key
//            Moesif_Application_Key = MonetizationUtils.getMoesifApplicationKey(tenantDomain);
////            String apiUrl = "https://api.moesif.com/v1/~/billing/catalog/plans?provider=stripe";
//
//            String createBillingPlanURL = MonetizationUtils.constructProviderURL
//                    (MoesifMonetizationConstants.BILLING_PLANS_URL, Provider.STRIPE);
//
            String apiName = api.getId().getApiName();
            String apiVersion = api.getId().getVersion();
            String apiProvider = api.getId().getProviderName();

            String moesifPlanName = apiName + "-" + apiVersion + "-" + apiProvider;
//
//            // JSON body
//            JsonObject createPlanPayload = new JsonObject();
//            createPlanPayload.addProperty("name", moesifPlanName);
//            createPlanPayload.addProperty("status", MoesifMonetizationConstants.BILLING_PLAN_STATUS_ACTIVE);
//            createPlanPayload.addProperty("provider", Provider.STRIPE.getValue());
//
////            createPlanPayload.addProperty("billing_type", "continuous_reporting");
////            createPlanPayload.addProperty("reporting_period", "5m");
//
//            // Send request
//            String planResponse = MonetizationUtils.
//                    sendPostRequest(createBillingPlanURL, createPlanPayload.toString(), Moesif_Application_Key);
//            log.info("Plan Response: " + planResponse);


            String moesifPlanResponse = createMoesifPlan(api, moesifApplicationKey, moesifPlanName);

            // Extract plan_id from response (assuming JSON like { "id": "prod_xxx", ... })
            String planId = extractId(moesifPlanResponse);
//            if (planId == null) {
//                throw new MonetizationException("Failed to extract plan_id from Moesif response");
//            }

//            String priceApiUrl = "https://api.moesif.com/v1/~/billing/catalog/prices?provider=stripe";

            String createPriceUrl = MonetizationUtils.constructProviderURL
                    (MoesifMonetizationConstants.BILLING_PRICE_URL, Provider.STRIPE);

            Map<String, String> tierPlanMap = new HashMap<String, String>();
            //scan for commercial tiers and add price to the above created plan
            for (Tier currentTier : api.getAvailableTiers()) {
                if (APIConstants.COMMERCIAL_TIER_PLAN.equalsIgnoreCase(currentTier.getTierPlan())) {
                    if (StringUtils.isNotBlank(planId)) {
//                        log.info("Current Tier " + currentTier);
//
//                        JsonObject createPricePayload = new JsonObject();
//                        createPricePayload.addProperty("name", currentTier.getName());
//                        createPricePayload.addProperty("provider", Provider.STRIPE.getValue());
//                        createPricePayload.addProperty("plan_id", planId);
//                        createPricePayload.addProperty("status", MoesifMonetizationConstants.BILLING_PRICE_STATUS_ACTIVE);
//
//                        if (!currentTier.getMonetizationAttributes().get("pricePerRequest").isEmpty()) {
//                            createPricePayload.addProperty("pricing_model", MoesifPricingModel.PER_UNIT.getValue());
//                            createPricePayload.addProperty("price_in_decimal", currentTier.getMonetizationAttributes().get("pricePerRequest"));
//
//                            //Create new billing meter for the new Product and Price
//                            JsonObject billingMeterPayload = new JsonObject();
//                            billingMeterPayload.addProperty("display_name", currentTier.getName() + " Meter");
//                            billingMeterPayload.addProperty("event_name", moesifPlanName + " Event");
//                            createPricePayload.add("price_meter", billingMeterPayload);
//
//                        } else if (!currentTier.getMonetizationAttributes().get("fixedPrice").isEmpty()) {
//                            createPricePayload.addProperty("pricing_model", MoesifPricingModel.FLAT_RATE.getValue());
//                            createPricePayload.addProperty("price_in_decimal", currentTier.getMonetizationAttributes().get("fixedPrice"));
//                            //This has to be further evaluated and attach a governance rule accordingly
//                            createPricePayload.addProperty("usage_aggregator", "");
//
//                        }
////                        createPricePayload.addProperty("usage_aggregator", "sum");
////                        createPricePayload.add("currency_prices", currencyPrices);
//
//                        //To_DO get these values from the UI
//                        createPricePayload.addProperty("period", 1);
//                        createPricePayload.addProperty("period_units", "M");
//                        createPricePayload.addProperty("currency", currentTier.getMonetizationAttributes().get("currencyType"));
//
//                        String priceResponse = MonetizationUtils.
//                                sendPostRequest(createPriceUrl, createPricePayload.toString(), moesifApplicationKey);

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
        return null;
    }

    @Override
    public Map<String, String> getCurrentUsageForSubscription(String s, APIProvider apiProvider) throws MonetizationException {
        return null;
    }

    @Override
    public Map<String, String> getTotalRevenue(API api, APIProvider apiProvider) throws MonetizationException {
        return null;
    }

    @Override
    public boolean publishMonetizationUsageRecords(MonetizationUsagePublishInfo monetizationUsagePublishInfo) throws MonetizationException {
        return false;
    }

    /**
     * Very basic extraction of "id" field from JSON response.
     * In production, better to use a JSON library like Jackson/Gson.
     */
    private String extractId(String jsonResponse) {
        // crude parsing just to avoid bringing in a dependency
        int idIndex = jsonResponse.indexOf("\"id\"");
        if (idIndex == -1) return null;
        int colonIndex = jsonResponse.indexOf(":", idIndex);
        int quoteStart = jsonResponse.indexOf("\"", colonIndex);
        int quoteEnd = jsonResponse.indexOf("\"", quoteStart + 1);
        if (quoteStart == -1 || quoteEnd == -1) return null;
        return jsonResponse.substring(quoteStart + 1, quoteEnd);
    }

    /**
     * Creates a billing plan in Moesif for the given API.
     *
     * @param api The API object for which the billing plan should be created.
     * @return The response from Moesif API after creating the billing plan.
     * @throws MonetizationException if any error occurs while creating the plan.
     */
    private String createMoesifPlan(API api, String moesifApplicationKey, String moesifPlanName) throws MoesifMonetizationException {

        String createBillingPlanURL = null;
        String planResponse = null;

        try {

            // Construct billing plan URL for provider (Stripe in this case)
            createBillingPlanURL = MonetizationUtils.constructProviderURL(
                    MoesifMonetizationConstants.BILLING_PLANS_URL, Provider.STRIPE);

            // Prepare JSON payload
            JsonObject createPlanPayload = new JsonObject();
            createPlanPayload.addProperty("name", moesifPlanName);
            createPlanPayload.addProperty("status", MoesifMonetizationConstants.BILLING_PLAN_STATUS_ACTIVE);
            createPlanPayload.addProperty("provider", Provider.STRIPE.getValue());

            // Send POST request to Moesif
            planResponse = MonetizationUtils.sendPostRequest(
                    createBillingPlanURL, createPlanPayload.toString(), moesifApplicationKey);

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
                    MoesifMonetizationConstants.BILLING_PRICE_URL, Provider.STRIPE);

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

            } else if (fixedPrice != null && !fixedPrice.isEmpty()) {
                // Flat-rate pricing model
                createPricePayload.addProperty("pricing_model", MoesifPricingModel.FLAT_RATE.getValue());
                createPricePayload.addProperty("price_in_decimal", fixedPrice);

                // Placeholder for governance rule attachment
                createPricePayload.addProperty("usage_aggregator", "");
            }

            // Common fields
            createPricePayload.addProperty("period", 1);
            createPricePayload.addProperty("period_units", "M");
            createPricePayload.addProperty("currency",
                    currentTier.getMonetizationAttributes().get("currencyType"));

            // Send POST request to Moesif
            priceResponse = MonetizationUtils.sendPostRequest(
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
