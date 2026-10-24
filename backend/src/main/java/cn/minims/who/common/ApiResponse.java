package cn.minims.who.common;

public record ApiResponse<T>(String code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>("OK", "ok", null);
    }

    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }
}
