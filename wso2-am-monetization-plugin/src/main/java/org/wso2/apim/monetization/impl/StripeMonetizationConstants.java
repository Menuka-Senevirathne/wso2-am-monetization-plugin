package org.wso2.apim.monetization.impl;

public class StripeMonetizationConstants {
    public static final String MONETIZATION_INFO = "MonetizationInfo";
    public static final String BILLING_ENGINE_PLATFORM_ACCOUNT_KEY = "BillingEnginePlatformAccountKey";

    public static final String ADD_MONETIZATION_DATA_SQL = "INSERT INTO AM_MONETIZATION_MOESIF VALUES (?,?,?,?,?)";

    public static final String GET_PRICE_ID_FOR_API_AND_TIER = "SELECT MOESIF_PRICE_ID FROM AM_MONETIZATION_MOESIF " +
            "WHERE API_ID = ? AND TIER_NAME = ?";

    public static final String GET_PLAN_INFO_FOR_API_AND_TIER = "SELECT MOESIF_PLAN_ID, MOESIF_PLAN_NAME, " +
            "MOESIF_PRICE_ID FROM AM_MONETIZATION_MOESIF WHERE API_ID = ? AND TIER_NAME = ?";

    public static final String INSERT_MONETIZATION_PLAN_DATA_SQL =
            "INSERT INTO AM_POLICY_PLAN_MAPPING (POLICY_UUID, PLAN_ID, PLAN_NAME, PRICE_ID,) VALUES (?,?,?,?)";

    public static final String INSERT_SUBSCRIPTION_DATA_SQL =
            " INSERT INTO AM_MONETIZATION_SUBSCRIPTIONS_MOESIF (SUBSCRIBED_API_ID, SUBSCRIBED_APPLICATION_ID," +
                    " TENANT_ID, CUSTOMER_ID, SUBSCRIPTION_ID)" +
                    " VALUES ((SELECT API_ID FROM AM_API WHERE API_UUID = ?),?,?,?,?)";

    public static final String GET_SUBSCRIPTION_UUID = "SELECT UUID FROM AM_SUBSCRIPTION WHERE SUBSCRIPTION_ID = ?";

    public static final String GET_BILLING_ENGINE_SUBSCRIPTION_ID =
            "SELECT SUBSCRIPTION_ID, CUSTOMER_ID FROM " +
                    "AM_MONETIZATION_SUBSCRIPTIONS_MOESIF " +
                    "WHERE SUBSCRIBED_APPLICATION_ID = ? AND SUBSCRIBED_API_ID = ?";


}
