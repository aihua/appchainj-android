package org.nervos.appchain.tests;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import org.nervos.appchain.abi.FunctionEncoder;
import org.nervos.appchain.abi.FunctionReturnDecoder;
import org.nervos.appchain.abi.TypeReference;
import org.nervos.appchain.abi.datatypes.Address;
import org.nervos.appchain.abi.datatypes.Function;
import org.nervos.appchain.abi.datatypes.Type;
import org.nervos.appchain.abi.datatypes.Uint;
import org.nervos.appchain.abi.datatypes.generated.Uint256;

import org.nervos.appchain.protocol.Nervosj;
import org.nervos.appchain.protocol.NervosjFactory;
import org.nervos.appchain.protocol.core.DefaultBlockParameter;
import org.nervos.appchain.protocol.core.DefaultBlockParameterName;
import org.nervos.appchain.protocol.core.methods.request.Call;
import org.nervos.appchain.protocol.core.methods.request.Transaction;
import org.nervos.appchain.protocol.core.methods.response.TransactionReceipt;
import org.nervos.appchain.protocol.http.HttpService;


public class TokenTransactionTest {
    private static Properties props;
    private static String testNetIpAddr;
    private static int chainId;
    private static int version;
    private static String privateKey;
    private static String fromAddress;
    private static String toAddress;
    private static String binPath;

    private static final String configPath
            = "tests/src/main/resources/config.properties";

    private static Random random;
    private static BigInteger quota;
    private static String value;
    private static Nervosj service;

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
        privateKey = props.getProperty(Config.SENDER_PRIVATE_KEY);
        fromAddress = props.getProperty(Config.SENDER_ADDR);
        toAddress = props.getProperty(Config.TEST_ADDR_1);
        binPath = props.getProperty(Config.TOKEN_BIN);

        HttpService.setDebug(false);
        service = NervosjFactory.build(new HttpService(testNetIpAddr));
        random = new Random(System.currentTimeMillis());
        quota = BigInteger.valueOf(1000000);
        value = "0";
    }

    static String loadContractCode(String binPath) throws Exception {
        return new String(Files.readAllBytes(Paths.get(binPath)));
    }

    static String deployContract(String contractCode) throws Exception {
        long currentHeight = service.appBlockNumber().send()
                .getBlockNumber().longValue();
        long validUntilBlock = currentHeight + 80;
        BigInteger nonce = BigInteger.valueOf(Math.abs(random.nextLong()));
        long quota = 9999999;
        Transaction tx = Transaction.createContractTransaction(
                nonce, quota, validUntilBlock,
                version, chainId, value, contractCode);
        String rawTx = tx.sign(privateKey, false, false);
        return service.appSendRawTransaction(rawTx)
                .send().getSendTransactionResult().getHash();
    }

    static TransactionReceipt getTransactionReceipt(String txHash)
            throws Exception {
        return service.appGetTransactionReceipt(txHash)
                .send().getTransactionReceipt();
    }

    static String contractFunctionCall(
            String contractAddress, String funcCallData) throws Exception {
        long currentHeight = service.appBlockNumber()
                .send().getBlockNumber().longValue();
        long validUntilBlock = currentHeight + 80;
        BigInteger nonce = BigInteger.valueOf(Math.abs(random.nextLong()));
        long quota = 1000000;

        Transaction tx = Transaction.createFunctionCallTransaction(
                contractAddress, nonce, quota, validUntilBlock,
                version, chainId, value, funcCallData);
        String rawTx = tx.sign(privateKey, false, false);

        return service.appSendRawTransaction(rawTx)
                .send().getSendTransactionResult().getHash();
    }

    static String transfer(
            String contractAddr, String toAddr, BigInteger value) throws Exception {
        Function transferFunc = new Function(
                "transfer",
                Arrays.<Type>asList(new Address(toAddr), new Uint256(value)),
                Collections.<TypeReference<?>>emptyList()
        );
        String funcCallData = FunctionEncoder.encode(transferFunc);
        return contractFunctionCall(contractAddr, funcCallData);
    }

    //eth_call
    static String call(
            String from, String contractAddress, String callData)
            throws Exception {
        Call call = new Call(from, contractAddress, callData);
        return service.appCall(call, DefaultBlockParameterName.fromString("latest")).send().getValue();
    }

    static String getBalance(String fromAddr, String contractAddress) throws Exception {
        Function getBalanceFunc = new Function(
                "getBalance",
                Arrays.<Type>asList(new Address(fromAddr)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint>() {
                })
        );
        String funcCallData = FunctionEncoder.encode(getBalanceFunc);
        String result = call(fromAddr, contractAddress, funcCallData);
        List<Type> resultTypes =
                FunctionReturnDecoder.decode(result, getBalanceFunc.getOutputParameters());
        return resultTypes.get(0).getValue().toString();
    }

    public static void main(String[] args) throws Exception {
        // deploy contract
        String contractCode = loadContractCode(binPath);
        System.out.println(contractCode);
        String deployContractTxHash = deployContract(contractCode);

        System.out.println("wait to deploy contract");
        Thread.sleep(10000);

        // get contract address from receipt
        TransactionReceipt txReceipt = getTransactionReceipt(deployContractTxHash);
        if (txReceipt.getErrorMessage() != null) {
            System.out.println("There is something wrong in deployContractTxHash. Error: "
                    + txReceipt.getErrorMessage());
            System.exit(1);
        }
        String contractAddress = txReceipt.getContractAddress();
        System.out.println("Contract deployed successfully. Contract address: "
                + contractAddress);

        // call contract function(eth_call)
        String balaneFrom = getBalance(fromAddress, contractAddress);
        String balanceTo = getBalance(toAddress, contractAddress);
        System.out.println(fromAddress + " has " + balaneFrom + " tokens.");
        System.out.println(toAddress + " has " + balanceTo + " tokens.");

        // call contract function
        String transferTxHash = transfer(contractAddress, toAddress, BigInteger.valueOf(1000));
        System.out.println("wait for transfer transaction.");
        Thread.sleep(10000);

        TransactionReceipt transferTxReceipt = getTransactionReceipt(transferTxHash);
        if (transferTxReceipt.getErrorMessage() != null) {
            System.out.println("Failed to call transfer method in contract. Error: "
                    + transferTxReceipt.getErrorMessage());
            System.exit(1);
        }
        System.out.println("call transfer method success and receipt is " + transferTxHash);

        balaneFrom = getBalance(fromAddress, contractAddress);
        balanceTo = getBalance(toAddress, contractAddress);
        System.out.println(fromAddress + " has " + balaneFrom + " tokens.");
        System.out.println(toAddress + " has " + balanceTo + " tokens.");

        System.out.println("Complete");
        System.exit(0);
    }
}
