package io.mycrypto.exception;

public class MyCustomException extends Exception {
    private String errorMessage;

    public MyCustomException(String msg) {
        super(msg);
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
