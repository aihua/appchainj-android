package org.nervos.appchain.tests;

import java.math.BigInteger;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.nervos.appchain.crypto.Credentials;
import org.nervos.appchain.protocol.Nervosj;
import org.nervos.appchain.protocol.NervosjFactory;
import org.nervos.appchain.protocol.core.DefaultBlockParameter;
import org.nervos.appchain.protocol.core.DefaultBlockParameterName;
import org.nervos.appchain.protocol.core.DefaultBlockParameterNumber;
import org.nervos.appchain.protocol.core.methods.response.TransactionReceipt;
import org.nervos.appchain.protocol.http.HttpService;
import org.nervos.appchain.tx.CitaTransactionManager;
import org.nervos.appchain.tx.TransactionManager;
import org.nervos.appchain.utils.Numeric;
import rx.functions.Action1;

public class TokenFilterTest {
    private static Properties props;
    private static String testNetIpAddr;
    private static int chainId;
    private static int version;
    private static String payerPrivateKey;
    private static String payeePrivateKey;
    private static Nervosj service;
    private static long quota;
    private static String value;
    private static Token token;

    private static final String configPath = "tests/src/main/resources/config.properties";

    static {
        try {
            props = Config.load(configPath);
        } catch (Exception e) {
            System.out.println("Failed to read properties from config file");
            e.printStackTrace();
        }

        chainId = Integer.parseInt(props.getProperty(Config.CHAIN_ID));
        version = Integer.parseInt(props.getProperty(Config.VERSION));
        testNetIpAddr = props.getProperty(Config.TEST_NET_ADDR);
        payerPrivateKey = props.getProperty(Config.SENDER_PRIVATE_KEY);
        payeePrivateKey = props.getProperty(Config.TEST_PRIVATE_KEY_1);

        HttpService.setDebug(false);
        service = NervosjFactory.build(new HttpService(testNetIpAddr));
        quota = 1000000L;
        value = "0";
    }

    static long getBalance(Credentials credentials) {
        long accountBalance = 0;
        try {
            Future<BigInteger> balanceFuture =
                    token.getBalance(credentials.getAddress()).sendAsync();
            accountBalance = balanceFuture.get(8, TimeUnit.SECONDS).longValue();
        } catch (Exception e) {
            System.out.println("Failed to get balance of account: " + credentials.getAddress());
            e.printStackTrace();
            System.exit(1);
        }
        return accountBalance;
    }

    private void eventObserve() {
        rx.Observable<Token.TransferEventResponse> observable =
                token.transferEventObservable(
                        DefaultBlockParameterNumber.valueOf(BigInteger.ONE),
                        DefaultBlockParameterName.LATEST);

        observable.subscribe(new Action1<Token.TransferEventResponse>() {
                                 @Override
                                 public void call(Token.TransferEventResponse transferEventResponse) {
                                     System.out.println(
                                             "Observable, TransferEvent(" + transferEventResponse._from + ", "
                                                     + transferEventResponse._to + ", "
                                                     + transferEventResponse._value.longValue() + ")");
                                 }
                             });
    }

    private void randomTransferToken() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                TokenFilterTest.this.eventObserve();
            }
        }).start();

        Credentials fromCredential = Credentials.create(payerPrivateKey);
        Credentials toCredential = Credentials.create(payeePrivateKey);

        for (int i = 0; i < 20; i++) {
            System.out.println("Transfer " + i);
            long fromBalance = getBalance(fromCredential);
            long transferAmount = ThreadLocalRandom
                    .current().nextLong(0, fromBalance);
            TransferEvent event = new TransferEvent(fromCredential, toCredential, transferAmount);
            System.out.println("Transaction " + event.toString() + " is being executing.");
            event.execute();
            try {
                Thread.sleep(10000);
            } catch (Exception e) {
                System.out.println("Thread interrupted.");
            }
        }
    }


    public static void main(String[] args) {
        TransactionManager citaTxManager = new CitaTransactionManager(
                service, Credentials.create(payerPrivateKey), 5, 3000);
        long validUtilBlock = TestUtil.getValidUtilBlock(service).longValue();
        BigInteger nonce = TestUtil.getNonce();

        Future<Token> tokenFuture = Token.deploy(
                service, citaTxManager, 1000000L, nonce,
                validUtilBlock, version,
                value, chainId).sendAsync();
        TokenFilterTest tokenFilterTest = new TokenFilterTest();

        Token contract = null;
        try {
            contract = tokenFuture.get();
        } catch (ExecutionException |InterruptedException e) {
            e.printStackTrace();
            System.out.println("Failed to get contract.");
            System.exit(1);
        }

        if(contract == null) {
            System.out.println("Contract is null.");
        }

        System.out.println("Contract deployment success. Contract address: " + contract.getContractAddress());

        try {
            System.out.println("Contract initial state: ");
            tokenFilterTest.randomTransferToken();
        } catch (Exception e) {
            System.out.println("Failed to get accounts balances");
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private class TransferEvent {
        Credentials from;
        Credentials to;
        long tokens;

        TransferEvent(Credentials from, Credentials to, long tokens) {
            this.from = from;
            this.to = to;
            this.tokens = tokens;
        }

        Future<TransactionReceipt> execute() {
            Token tokenContract = TokenFilterTest.this.token;
            long validUtilBlock = TestUtil.getValidUtilBlock(
                    TokenFilterTest.this.service).longValue();
            BigInteger nonce = TestUtil.getNonce();
            return tokenContract.transfer(
                    this.to.getAddress(), BigInteger.valueOf(tokens), TokenFilterTest.this.quota,
                    nonce, validUtilBlock, version, chainId, value).sendAsync();
        }

        @Override
        public String toString() {
            return "TransferEvent(" + this.from.getAddress()
                    + ", " + this.to.getAddress() + ", " + this.tokens + ")";
        }
    }
}
