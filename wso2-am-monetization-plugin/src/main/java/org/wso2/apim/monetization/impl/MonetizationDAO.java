package org.wso2.apim.monetization.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.api.model.policy.SubscriptionPolicy;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import static org.apache.commons.io.filefilter.TrueFileFilter.INSTANCE;

public class MonetizationDAO {
    private static MonetizationDAO INSTANCE = null;
    private static final Log log = LogFactory.getLog(MonetizationDAO.class);
    private ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();

    public static MonetizationDAO getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MonetizationDAO();
        }
        return INSTANCE;
    }

    public void addMonetizationData(int apiId, String planId, String planName, Map<String, String> tierPlanMap) throws MoesifMonetizationException {

        PreparedStatement preparedStatement = null;
        Connection connection = null;
        boolean initialAutoCommit = false;
        try {
            if (!tierPlanMap.isEmpty()) {
                connection = APIMgtDBUtil.getConnection();
                preparedStatement = connection.prepareStatement(StripeMonetizationConstants.ADD_MONETIZATION_DATA_SQL);
                initialAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                for (Map.Entry<String, String> entry : tierPlanMap.entrySet()) {
                    preparedStatement.setInt(1, apiId);
                    preparedStatement.setString(2, entry.getKey());
                    preparedStatement.setString(3, planId);
                    preparedStatement.setString(4, planName);
                    preparedStatement.setString(5, entry.getValue());
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
                connection.commit();
            }
        } catch (SQLException e) {
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException ex) {
                String errorMessage = "Failed to rollback add monetization data for API : " + apiId;
                log.error(errorMessage, e);
                throw new MoesifMonetizationException(errorMessage, e);
            } finally {
                APIMgtDBUtil.setAutoCommit(connection, initialAutoCommit);
            }
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, connection, null);
        }
    }

    public String getPriceIdForTier(int apiID, String tierName) {

        Connection connection = null;
        PreparedStatement statement = null;
        String priceId = StringUtils.EMPTY;
        try {
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            statement = connection.prepareStatement(StripeMonetizationConstants.GET_PRICE_ID_FOR_API_AND_TIER);
            statement.setInt(1, apiID);
            statement.setString(2, tierName);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                priceId = rs.getString("MOESIF_PRICE_ID");
            }
            connection.commit();
        } catch (SQLException e) {
            String errorMessage = "Failed to get Price ID for tier : " + tierName;
//            log.error(errorMessage, e);
//            throw new StripeMonetizationException(errorMessage, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(statement, connection, null);
        }
        return priceId;
    }

    public MoesifPlanInfo getPlanInfoForTier(int apiID, String tierName) {
        Connection connection = null;
        PreparedStatement statement = null;
        MoesifPlanInfo planInfo = null;
        try {
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            statement = connection.prepareStatement(StripeMonetizationConstants.GET_PLAN_INFO_FOR_API_AND_TIER);
            statement.setInt(1, apiID);
            statement.setString(2, tierName);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                planInfo = new MoesifPlanInfo(
                        rs.getString("MOESIF_PLAN_ID"),
                        rs.getString("MOESIF_PRICE_ID"),
                        rs.getString("MOESIF_PLAN_NAME")


                );
            }
            connection.commit();
        } catch (SQLException e) {
            String errorMessage = "Failed to get Plan Info for tier : " + tierName;
//        log.error(errorMessage, e);
//        throw new StripeMonetizationException(errorMessage, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(statement, connection, null);
        }
        return planInfo;
    }


    public void addMonetizationPlanData(SubscriptionPolicy policy, String planId, String planName, String priceId) {

        Connection conn = null;
        PreparedStatement policyStatement = null;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            policyStatement = conn.prepareStatement(StripeMonetizationConstants.INSERT_MONETIZATION_PLAN_DATA_SQL);
            policyStatement.setString(1, apiMgtDAO.getSubscriptionPolicy(policy.getPolicyName(),
                    policy.getTenantId()).getUUID());
            policyStatement.setString(2, planId);
            policyStatement.setString(3, planName);
            policyStatement.setString(3, priceId);
            policyStatement.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    String errorMessage = "Failed to rollback adding monetization plan for : " + policy.getPolicyName();
//                    log.error(errorMessage);
//                    throw new StripeMonetizationException(errorMessage, ex);
                }
            }
            String errorMessage = "Failed to add monetization plan for : " + policy.getPolicyName();
//            log.error(errorMessage);
//            throw new StripeMonetizationException(errorMessage, e);
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get subscription policy : " + policy.getPolicyName() +
                    " from database when creating stripe plan.";
//            log.error(errorMessage);
//            throw new StripeMonetizationException(errorMessage, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(policyStatement, conn, null);
        }
    }

    public void addSubscription(APIIdentifier identifier, int applicationId, int tenantId, String customerId,
                                  String subscriptionId, String apiUuid) throws StripeMonetizationException {

        Connection conn = null;
        ResultSet rs = null;
        PreparedStatement ps = null;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            String query = StripeMonetizationConstants.INSERT_SUBSCRIPTION_DATA_SQL;
            ps = conn.prepareStatement(query);
            ps.setString(1, apiUuid);
            ps.setInt(2, applicationId);
            ps.setInt(3, tenantId);
            ps.setString(4, customerId);
            ps.setString(5, subscriptionId);
            ps.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    log.error("Error while rolling back the failed operation", ex);
                }
            }
            String errorMessage = "Failed to add Stripe subscription info for API : " + identifier.getApiName() + " by"
                    + " Application : " + applicationId;
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
    }

    public String getSubscriptionUUID(int subscriptionId) throws StripeMonetizationException {

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        String subscriptionUUID = null;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            ps = conn.prepareStatement(StripeMonetizationConstants.GET_SUBSCRIPTION_UUID);
            ps.setInt(1, subscriptionId);
            rs = ps.executeQuery();
            while (rs.next()) {
                subscriptionUUID = rs.getString("UUID");
            }
        } catch (SQLException e) {
            String errorMessage = "Error while getting UUID of subscription ID : " + subscriptionId;
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return subscriptionUUID;
    }

    public MonetizedStripeSubscriptionInfo getMonetizedSubscription(int apiId, int applicationId)
            throws StripeMonetizationException {

        MonetizedStripeSubscriptionInfo subscription = null;
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet rs = null;

        try {
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            statement = connection.prepareStatement(
                    StripeMonetizationConstants.GET_BILLING_ENGINE_SUBSCRIPTION_ID);
            statement.setInt(1, applicationId);
            statement.setInt(2, apiId);

            rs = statement.executeQuery();
            if (rs.next()) {
                String subscriptionId = rs.getString("SUBSCRIPTION_ID");
                String customerId = rs.getString("CUSTOMER_ID");
                subscription = new MonetizedStripeSubscriptionInfo(subscriptionId, customerId);
            }
            connection.commit();
        } catch (SQLException e) {
            String errorMessage = "Failed to get billing engine subscription for API : " + apiId +
                    " and application ID : " + applicationId;
            log.error(errorMessage, e);
            throw new StripeMonetizationException(errorMessage, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(statement, connection, rs);
        }
        return subscription;
    }

}
