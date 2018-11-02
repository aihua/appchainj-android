package org.nervos.appchain.protocol.core.filters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.nervos.appchain.protocol.Nervosj;
import org.nervos.appchain.protocol.NervosjFactory;
import org.nervos.appchain.protocol.NervosjService;

import org.nervos.appchain.protocol.ObjectMapperFactory;
import org.nervos.appchain.protocol.core.Request;
import org.nervos.appchain.protocol.core.methods.response.AppFilter;
import org.nervos.appchain.protocol.core.methods.response.AppLog;
import org.nervos.appchain.protocol.core.methods.response.AppUninstallFilter;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class FilterTester {

    private NervosjService nervosjService;
    Nervosj nervosj;

    final ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
    final ScheduledExecutorService scheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor();

    @Before
    public void setUp() {
        nervosjService = mock(NervosjService.class);
        nervosj = NervosjFactory.build(nervosjService, 1000, scheduledExecutorService);
    }

    <T> void runTest(AppLog appLog, Observable<T> observable) throws Exception {
        AppFilter appFilter = objectMapper.readValue(
                "{\n"
                        + "  \"id\":1,\n"
                        + "  \"jsonrpc\": \"2.0\",\n"
                        + "  \"result\": \"0x1\"\n"
                        + "}", AppFilter.class);

        AppUninstallFilter appUninstallFilter = objectMapper.readValue(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":true}", AppUninstallFilter.class);

        @SuppressWarnings("unchecked")
        List<T> expected = createExpected(appLog);
        final Set<T> results = Collections.synchronizedSet(new HashSet<T>());

        final CountDownLatch transactionLatch = new CountDownLatch(expected.size());

        final CountDownLatch completedLatch = new CountDownLatch(1);

        when(nervosjService.send(any(Request.class), eq(AppFilter.class)))
                .thenReturn(appFilter);
        when(nervosjService.send(any(Request.class), eq(AppLog.class)))
                .thenReturn(appLog);
        when(nervosjService.send(any(Request.class), eq(AppUninstallFilter.class)))
                .thenReturn(appUninstallFilter);

        Subscription subscription = observable.subscribe(
                new Action1<T>() {
                    @Override
                    public void call(T result) {
                        results.add(result);
                        transactionLatch.countDown();
                    }
                },
                new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        fail(throwable.getMessage());
                    }
                },
                new Action0() {
                    @Override
                    public void call() {
                        completedLatch.countDown();
                    }
                });

        transactionLatch.await(1, TimeUnit.SECONDS);
        assertThat(results, CoreMatchers.<Set<T>>equalTo(new HashSet<>(expected)));

        subscription.unsubscribe();

        completedLatch.await(1, TimeUnit.SECONDS);
        assertTrue(subscription.isUnsubscribed());
    }

    List createExpected(AppLog appLog) {
        List<AppLog.LogResult> logResults = appLog.getLogs();
        if (logResults.isEmpty()) {
            fail("Results cannot be empty");
        }

        List expected = new ArrayList();
        for (AppLog.LogResult logResult : appLog.getLogs()) {
            expected.add(logResult.get());
        }
        return expected;
    }
}
