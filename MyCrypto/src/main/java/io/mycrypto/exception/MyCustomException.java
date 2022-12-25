package io.mycrypto.exception;

import io.mycrypto.util.Utility;
import org.json.simple.JSONObject;

public class MyCustomException extends Exception {
    private String errorMessage;

    public MyCustomException(String msg) {
        super(msg);
        this.errorMessage = msg;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public JSONObject getMessageAsJSONString() {
        return Utility.constructJsonResponse("err", this.errorMessage);
    }
}
