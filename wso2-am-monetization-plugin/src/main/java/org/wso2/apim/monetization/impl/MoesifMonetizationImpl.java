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
import java.util.HashMap;
import java.util.Map;


public class MoesifMonetizationImpl implements Monetization {
    private static final Log log = LogFactory.getLog(MoesifMonetizationImpl.class);

    @Override
    public boolean createBillingPlan(SubscriptionPolicy subscriptionPolicy) throws MonetizationException {

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
        //Create product resembles the API
        String Moesif_Application_Key;
        try {
            //read tenant conf and get application account key
            Moesif_Application_Key = MonetizationUtils.getMoesifApplicationKey(tenantDomain);
//            String apiUrl = "https://api.moesif.com/v1/~/billing/catalog/plans?provider=stripe";

            String createBillingPlanURL = MonetizationUtils.constructProviderURL
                    (MoesifMonetizationConstants.BILLING_PLANS_URL, Provider.STRIPE);

            String apiName = api.getId().getApiName();
            String apiVersion = api.getId().getVersion();
            String apiProvider = api.getId().getProviderName();

            String moesifProductName = apiName + "-" + apiVersion + "-" + apiProvider;

            // JSON body
            JsonObject createPlanPayload = new JsonObject();
            createPlanPayload.addProperty("name", moesifProductName);
            createPlanPayload.addProperty("status", MoesifMonetizationConstants.BILLING_PLAN_STATUS_ACTIVE);
            createPlanPayload.addProperty("provider", Provider.STRIPE.toString());

//            createPlanPayload.addProperty("billing_type", "continuous_reporting");
//            createPlanPayload.addProperty("reporting_period", "5m");

            // Send request
            String planResponse = MonetizationUtils.sendPostRequest(createBillingPlanURL, createPlanPayload.toString(), Moesif_Application_Key);
            log.info("Plan Response: " + planResponse);

            // Extract plan_id from response (assuming JSON like { "id": "prod_xxx", ... })
            String planId = extractPlanId(planResponse);
            if (planId == null) {
                throw new MonetizationException("Failed to extract plan_id from Moesif response");
            }

//            String priceApiUrl = "https://api.moesif.com/v1/~/billing/catalog/prices?provider=stripe";

            String createPriceUrl = MonetizationUtils.constructProviderURL
                    (MoesifMonetizationConstants.BILLING_PRICE_URL, Provider.STRIPE);

            Map<String, String> tierPlanMap = new HashMap<String, String>();
            //scan for commercial tiers and add price to the above created plan
            for (Tier currentTier : api.getAvailableTiers()) {
                if (APIConstants.COMMERCIAL_TIER_PLAN.equalsIgnoreCase(currentTier.getTierPlan())) {
                    if (StringUtils.isNotBlank(planId)) {
                        log.info("Current Tier " + currentTier);
                        int tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
                        JsonObject currencyPrices = new JsonObject();
//                        currencyPrices.addProperty("USD", "1"); // <-- your dynamic price here

                        JsonObject createPricePayload = new JsonObject();
                        createPricePayload.addProperty("name", currentTier.getName());
                        createPricePayload.addProperty("provider", Provider.STRIPE.toString());
                        createPricePayload.addProperty("plan_id", planId);
                        createPricePayload.addProperty("status", MoesifMonetizationConstants.BILLING_PRICE_STATUS_ACTIVE);

                        //Prepare ENUM based on Moesif
                        createPricePayload.addProperty("pricing_model", "per_unit");

//                        createPricePayload.addProperty("usage_aggregator", "sum");
//                        createPricePayload.add("currency_prices", currencyPrices);

                        //To_DO get these values from the UI
                        createPricePayload.addProperty("period", 1);
                        createPricePayload.addProperty("period_units", "M");
                        createPricePayload.addProperty("price_in_decimal", currentTier.getMonetizationAttributes().get("pricePerRequest"));
                        createPricePayload.addProperty("currency", currentTier.getMonetizationAttributes().get("currencyType"));

                        //Create new billing meter for the new Product and Price
                        JsonObject billingMeterPayload = new JsonObject();
                        billingMeterPayload.addProperty("display_name",currentTier.getName()+" Meter");
                        billingMeterPayload.addProperty("event_name",moesifProductName+" Event");
                        createPricePayload.add("price_meter",billingMeterPayload);

                        String priceResponse = MonetizationUtils.sendPostRequest(createPriceUrl, createPricePayload.toString(), Moesif_Application_Key);

                        log.info("Price Response: " + priceResponse);

                    }
                }
            }

        } catch (MoesifMonetizationException e) {
            String errorMessage = "Failed to get Moesif account key for tenant :  " +
                    tenantDomain;
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return  true;
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

//    private String getMoesifApplicationKey(String tenantDomain) throws MoesifMonetizationException {
//
//        try {
//            //get the application key of platform account from tenant conf json file
//            JSONObject tenantConfig = APIUtil.getTenantConfig(tenantDomain);
//
//            if (tenantConfig.containsKey(MoesifMonetizationConstants.MONETIZATION_INFO)) {
//                JSONObject monetizationInfo = (JSONObject) tenantConfig
//                        .get(MoesifMonetizationConstants.MONETIZATION_INFO);
//                if (monetizationInfo.containsKey(MoesifMonetizationConstants.MOESIF_APPLICATION_KEY)) {
//                    String moesifApplicationKey = monetizationInfo
//                            .get(MoesifMonetizationConstants.MOESIF_APPLICATION_KEY).toString();
//                    if (StringUtils.isBlank(moesifApplicationKey)) {
//                        String errorMessage = "Moesif application key is empty for tenant : " + tenantDomain;
//                        throw new MoesifMonetizationException(errorMessage);
//                    }
//                    return moesifApplicationKey;
//                }
//            }
//        } catch (APIManagementException e) {
//            String errorMessage = "Failed to get the configuration for tenant from DB:  " + tenantDomain;
//            log.error(errorMessage);
//            throw new MoesifMonetizationException(errorMessage, e);
//        }
//        return StringUtils.EMPTY;
//    }

//    private String sendPostRequest(String apiUrl, String createPlanPayload, String token) throws IOException {
//        URL url = new URL(apiUrl);
//        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//        conn.setRequestMethod("POST");
//        conn.setRequestProperty("Content-Type", "application/json");
//        conn.setRequestProperty("Accept", "application/json");
//        conn.setRequestProperty("Authorization", "Bearer " + token);
//        conn.setDoOutput(true);
//
//        try (OutputStream os = conn.getOutputStream()) {
//            byte[] input = createPlanPayload.getBytes(StandardCharsets.UTF_8);
//            os.write(input, 0, input.length);
//        }
//
//        int responseCode = conn.getResponseCode();
//        StringBuilder response = new StringBuilder();
//        try (java.io.BufferedReader br = new java.io.BufferedReader(
//                new java.io.InputStreamReader(
//                        responseCode >= 200 && responseCode < 300 ? conn.getInputStream() : conn.getErrorStream(),
//                        StandardCharsets.UTF_8))) {
//            String responseLine;
//            while ((responseLine = br.readLine()) != null) {
//                response.append(responseLine.trim());
//            }
//        }
//        conn.disconnect();
//        return response.toString();
//    }

    /**
     * Very basic extraction of "id" field from JSON response.
     * In production, better to use a JSON library like Jackson/Gson.
     */
    private String extractPlanId(String jsonResponse) {
        // crude parsing just to avoid bringing in a dependency
        int idIndex = jsonResponse.indexOf("\"id\"");
        if (idIndex == -1) return null;
        int colonIndex = jsonResponse.indexOf(":", idIndex);
        int quoteStart = jsonResponse.indexOf("\"", colonIndex);
        int quoteEnd = jsonResponse.indexOf("\"", quoteStart + 1);
        if (quoteStart == -1 || quoteEnd == -1) return null;
        return jsonResponse.substring(quoteStart + 1, quoteEnd);
    }


}
