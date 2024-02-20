package org.example;

import java.time.Duration;
import java.util.concurrent.*;

public class RequestHandler implements Handler {
    private final Client client;
    private int retriesCount = 0;
    private Duration lastRequestTime = Duration.ZERO;

    public RequestHandler(ClientHandler client) {
        this.client = client;
    }

    @Override
    public ApplicationStatusResponse perfomOperation(String id) throws ExecutionException, InterruptedException {
        CompletableFuture<ApplicationStatusResponse> responseFuture = new CompletableFuture<>();
        return executeAsync(id, Duration.ofMillis(0));
    }

    private ApplicationStatusResponse executeAsync(String id, Duration delay) throws ExecutionException, InterruptedException {
        CompletableFuture<ApplicationStatusResponse> responseFuture = new CompletableFuture<>();
        CompletableFuture<Response> response1Future = CompletableFuture.supplyAsync(() -> client.getApplicationStatus1(id));
        CompletableFuture<Response> response2Future = CompletableFuture.supplyAsync(() -> client.getApplicationStatus2(id));

        CompletableFuture<Void> delayFuture = CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS));

        CompletableFuture<Object> combinedFuture = delayFuture.thenComposeAsync(__ -> CompletableFuture.anyOf(response1Future, response2Future));

        CompletableFuture<ApplicationStatusResponse> resultFuture = combinedFuture.thenApplyAsync(result -> {
                    if (result instanceof Response.Success successResponse) {
                        return
                                new ApplicationStatusResponse.Success(successResponse.applicationId(), successResponse.applicationStatus()
                                );
                    } else if (result instanceof Response.RetryAfter retryAfterResponse) {
                        retriesCount++;
                        lastRequestTime = Duration.ofMillis(System.currentTimeMillis());
                        Duration delayNew = retryAfterResponse.delay();

                        try {
                            return executeAsync(id, delayNew);
                        } catch (ExecutionException | InterruptedException e) {
                            return new ApplicationStatusResponse.Failure(lastRequestTime, retriesCount);
                        }
                    } else if (result instanceof Response.Failure failureResponse) {
                        lastRequestTime = Duration.ofMillis(System.currentTimeMillis());
                        retriesCount++;
                        return new ApplicationStatusResponse.Failure(lastRequestTime, retriesCount);
                    }

                    return new ApplicationStatusResponse.Failure(lastRequestTime, retriesCount);
                });
        System.out.println(resultFuture.get());
        return resultFuture.get();
    }
}
