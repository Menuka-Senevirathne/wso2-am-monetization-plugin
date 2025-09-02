package org.wso2.apim.monetization.impl;

public class StripeMonetizationException extends Exception {

    public StripeMonetizationException(String msg) {
        super(msg);
    }

    public StripeMonetizationException(String msg, Throwable e) {
        super(msg, e);
    }

    public StripeMonetizationException(Throwable throwable) {
        super(throwable);
    }

}
