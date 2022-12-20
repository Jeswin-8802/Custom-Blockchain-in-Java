package io.mycrypto.exception;

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
}
