package org.nervos.appchain.tx;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.Future;

import org.nervos.appchain.crypto.Credentials;
import org.nervos.appchain.protocol.AppChainj;
import org.nervos.appchain.protocol.core.DefaultBlockParameterName;
import org.nervos.appchain.protocol.core.methods.request.Transaction;
import org.nervos.appchain.protocol.core.methods.response.AppGetTransactionCount;
import org.nervos.appchain.protocol.core.methods.response.AppSendTransaction;

public class CitaTransactionManager extends TransactionManager {

    private final AppChainj appChainj;
    final Credentials credentials;

    public CitaTransactionManager(AppChainj appChainj, Credentials credentials) {
        super(appChainj, credentials.getAddress());
        this.appChainj = appChainj;
        this.credentials = credentials;

    }

    public CitaTransactionManager(
            AppChainj appChainj, Credentials credentials, int attempts, int sleepDuration) {
        super(appChainj, attempts, sleepDuration, credentials.getAddress());
        this.appChainj = appChainj;
        this.credentials = credentials;
    }

    BigInteger getNonce() throws IOException {
        AppGetTransactionCount ethGetTransactionCount = appChainj.appGetTransactionCount(
                credentials.getAddress(), DefaultBlockParameterName.LATEST).send();

        return ethGetTransactionCount.getTransactionCount();
    }

    @Override
    public AppSendTransaction sendTransaction(
            BigInteger quota, BigInteger nonce, String to,
            String data, String value) throws IOException {
        return new AppSendTransaction();
    }

    // adapt to cita
    @Override
    public AppSendTransaction sendTransaction(
            String to, String data, long quota, BigInteger nonce,
            long validUntilBlock, int version, int chainId, String value)
            throws IOException {
        Transaction transaction = new Transaction(
                to, nonce, quota, validUntilBlock,
                version, chainId, value, data);
        return appChainj.appSendRawTransaction(transaction.sign(credentials)).send();
    }

    // adapt to cita
    public Future<AppSendTransaction> sendTransactionAsync(
            String to, String data, long quota, BigInteger nonce,
            long validUntilBlock, int version, int chainId, String value) {
        Transaction transaction = new Transaction(
                to, nonce, quota, validUntilBlock,
                version, chainId, value, data);
        return appChainj.appSendRawTransaction(transaction.sign(credentials)).sendAsync();
    }

    @Override
    public String getFromAddress() {
        return credentials.getAddress();
    }
}
