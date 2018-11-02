package org.nervos.appchain.tests;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import java.util.Random;

import org.nervos.appchain.abi.FunctionEncoder;
import org.nervos.appchain.abi.FunctionReturnDecoder;
import org.nervos.appchain.abi.TypeReference;
import org.nervos.appchain.abi.datatypes.Function;
import org.nervos.appchain.abi.datatypes.Type;
import org.nervos.appchain.abi.datatypes.generated.Uint256;
import org.nervos.appchain.protocol.Nervosj;
import org.nervos.appchain.protocol.NervosjFactory;
import org.nervos.appchain.protocol.core.DefaultBlockParameterName;
import org.nervos.appchain.protocol.core.methods.request.Call;
import org.nervos.appchain.protocol.core.methods.request.Transaction;
import org.nervos.appchain.protocol.core.methods.response.TransactionReceipt;
import org.nervos.appchain.protocol.http.HttpService;

public class AdvanceTransactionTest {
    static final String configPath = "tests/src/main/resources/config.properties";

    static Properties props;
    static Nervosj service;
    static String senderPrivateKey;
    static int version;
    static int chainId;

    static {
        props = Config.load(configPath);
        service = NervosjFactory.build(new HttpService(props.getProperty(Config.TEST_NET_ADDR)));
        senderPrivateKey = props.getProperty(Config.SENDER_PRIVATE_KEY);
        version = Integer.parseInt(props.getProperty(Config.VERSION));
        chainId = Integer.parseInt(props.getProperty(Config.CHAIN_ID));
    }

    private Random random;
    private long quota = 50000;
    private long validUntilBlock;
    private int sendCount;
    private int threadCount;
    private boolean isEd25519AndBlake2b;
    private String contractAddress;
    private String value = "0";

    public AdvanceTransactionTest(int sdCount, int thdCount, boolean isEd25519AndBlake2b) {
        try {
            random = new Random(System.currentTimeMillis());
            this.validUntilBlock = TestUtil.getValidUtilBlock(service, 100).longValue();
            this.sendCount = sdCount;
            this.threadCount = thdCount;
            this.isEd25519AndBlake2b = isEd25519AndBlake2b;
            System.out.println("Initial block height: " + TestUtil.getCurrentHeight(service));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    // Deploy contract, return transaction hash
    private String deployContract(boolean isEd25519AndBlake2b) throws Exception {
        // contract.bin
        String contractCode = "606060405260008055341561001357600080f"
                + "d5b60fc806100216000396000f30060606040526004361060"
                + "525763ffffffff7c010000000000000000000000000000000"
                + "00000000000000000000000006000350416634f2be91f8114"
                + "60575780636d4ce63c146069578063d826f88f14608b575b6"
                + "00080fd5b3415606157600080fd5b6067609b565b005b3415"
                + "607357600080fd5b607960b2565b604051908152602001604"
                + "05180910390f35b3415609557600080fd5b606760b8565b60"
                + "005460ad90600163ffffffff60be16565b600055565b60005"
                + "490565b60008055565b8181018281101560ca57fe5b929150"
                + "505600a165627a7a72305820870dd43666c8c38bef68662f8"
                + "4b2c0e537643ad06bd44e4fb85b76114f99d8750029";
        BigInteger nonce = BigInteger.valueOf(Math.abs(this.random.nextLong()));
        Transaction tx = Transaction.createContractTransaction(
                nonce, 99999, this.validUntilBlock,
                version, chainId, value, contractCode);

        String rawTx = tx.sign(senderPrivateKey, isEd25519AndBlake2b, false);

        return service.appSendRawTransaction(rawTx)
                .send().getSendTransactionResult().getHash();
    }

    // Contract function reset call
    private String funcResetCall(
            String contractAddress, boolean isEd25519AndBlake2b)
            throws Exception {
        Function resetFunc = new Function(
                "reset",
                Collections.<Type>emptyList(),
                Collections.<TypeReference<?>>emptyList()
        );

        String resetFuncData = FunctionEncoder.encode(resetFunc);

        BigInteger nonce = TestUtil.getNonce();
        Transaction tx = Transaction.createFunctionCallTransaction(
                contractAddress,
                nonce,
                this.quota,
                this.validUntilBlock,
                version,
                chainId,
                value,
                resetFuncData);
        String rawTx = tx.sign(senderPrivateKey, isEd25519AndBlake2b, false);

        return service.appSendRawTransaction(rawTx)
                .send().getSendTransactionResult().getHash();
    }

    // Contract function add call
    public void funcAddCall(
            String contractAddress, boolean isEd25519AndBlake2b)
            throws Exception {
        Function addFunc = new Function(
                "add",
                Collections.<Type>emptyList(),
                Collections.<TypeReference<?>>emptyList()
        );
        String addFuncData = FunctionEncoder.encode(addFunc);

        BigInteger nonce = BigInteger.valueOf(
                Math.abs(this.random.nextLong()));
        Transaction tx = Transaction.createFunctionCallTransaction(
                contractAddress,
                nonce,
                this.quota,
                this.validUntilBlock,
                version,
                chainId,
                value,
                addFuncData);
        String rawTx = tx.sign(senderPrivateKey, isEd25519AndBlake2b, false);

        service.appSendRawTransaction(rawTx).send();
    }

    // appCall
    private String call(
            String from, String contractAddress, String callData)
            throws Exception {
        Call call = new Call(from, contractAddress, callData);
        return service.appCall(
                call, DefaultBlockParameterName.LATEST).send().getValue();
    }

    // Get transaction receipt
    private TransactionReceipt getTransactionReceipt(String txHash)
            throws Exception {
        return service.appGetTransactionReceipt(txHash)
                .send().getTransactionReceipt();
    }

    public void runContract() throws Exception {
        final boolean isEd25519AndBlake2b = this.isEd25519AndBlake2b;
        long dealWithCount = 0;
        String appCallResult;
        String from = "0x0dbd369a741319fa5107733e2c9db9929093e3c7";
        long startBlock = TestUtil.getCurrentHeight(service).longValue();
        long oldBlock = startBlock;
        long currentBlock = startBlock;

        // deploy contract
        String deployContractTxHash = deployContract(isEd25519AndBlake2b);
        System.out.println("wait to deploy contract , txHash: "
                + deployContractTxHash);

        // wait for contract deployment.
        // don't wait for 3 more blocks
        // because block height increase might stop or even decrease in some case.
        System.out.println("Wait for contract deployment. ");
        Thread.sleep(10000);

        // get contract address from receipt
        int countForContractDeployment = 0;
        while (true) {
            TransactionReceipt receipt = service
                    .appGetTransactionReceipt(deployContractTxHash)
                    .send().getTransactionReceipt();
            if (receipt != null) {
                TransactionReceipt deployTxReceipt = receipt;
                if (deployTxReceipt.getErrorMessage() == null) {
                    this.contractAddress = deployTxReceipt.getContractAddress();
                    System.out.println("Contract is deployed successfully.");
                    break;
                } else {
                    System.out.println("Failed to deploy smart contract. Error: "
                            + deployTxReceipt.getErrorMessage());
                    System.exit(1);
                }
            } else {
                System.out.println("Waiting for contract deployment....");
                Thread.sleep(3000);
                if (countForContractDeployment++ > 3) {
                    System.out.println("Timeout, failed to deploy contract.");
                    System.exit(1);
                }
            }
        }

        String resetTxHash = funcResetCall(this.contractAddress, isEd25519AndBlake2b);

        //call smart contract function and wait for receipt.
        int countForResetFunctionCall = 0;
        while (true) {
            TransactionReceipt receipt = service
                    .appGetTransactionReceipt(resetTxHash)
                    .send().getTransactionReceipt();
            if (receipt != null) {
                TransactionReceipt resetTxReceipt = receipt;
                if (resetTxReceipt.getErrorMessage() == null) {
                    System.out.println("Count is reset successfully.");
                    break;
                } else {
                    System.out.println("Failed to reset count.");
                    System.exit(1);
                }
            } else {
                System.out.println("Waiting to reset count....");
                Thread.sleep(3000);
                if (countForResetFunctionCall++ > 3) {
                    System.out.println("Timeout, failed to reset count.");
                    System.exit(1);
                }
            }
        }


        System.out.println("Contract address: " + this.contractAddress + ", start call add...");
        long startTime = System.currentTimeMillis();
        //start thread
        Thread[] threads = new Thread[this.threadCount];
        final int countPerThread = this.sendCount;
        for (int i = 0; i < this.threadCount; i++) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        int j = 0;
                        while (j < countPerThread) {
                            funcAddCall(contractAddress, isEd25519AndBlake2b);
                            j++;
                        }
                    } catch (Exception e) {
                        System.out.println("Failed to call contract function.");
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
            });
            t.start();
            threads[i] = t;
        }

        for (int k = 0; k < this.threadCount; k++) {
            threads[k].join();
        }
        System.out.println("send tx use "
                + (System.currentTimeMillis() - startTime) + " ms");

        //get result
        while (true) {
            Thread.sleep(2000);
            currentBlock = TestUtil.getCurrentHeight(service).longValue();
            if (currentBlock < oldBlock) {
                System.out.println("Error : Height is decreasing");
            }
            oldBlock = currentBlock;
            if (currentBlock >= this.validUntilBlock) {
                break;
            }

            Function getCall = new Function(
                    "get",
                    Collections.<Type>emptyList(),
                    Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {
                    })
            );

            String getCallData = FunctionEncoder.encode(getCall);
            appCallResult = call(from, contractAddress, getCallData);
            String appCallResultReadable = FunctionReturnDecoder.decode(
                    appCallResult, getCall.getOutputParameters())
                    .get(0).toString();
            System.out.println("appCallResult: " + appCallResultReadable);
            if (appCallResult == null || appCallResult.length() < 3) {
                continue;
            }
            dealWithCount = Long.valueOf(appCallResult.substring(2), 16);
            System.out.println("app_call result: " + dealWithCount);
            if (dealWithCount == this.sendCount * this.threadCount) {
                break;
            }
        }
        long endTime = System.currentTimeMillis();

        System.out.println("complete input count : " + this.sendCount * this.threadCount
                + ", actual count: " + dealWithCount);
        System.out.println("performance(transactioncount/s):");
        System.out.println("start time: " + startTime
                + " ms\r\n" + "end   time: " + endTime + " ms");
        double tps = dealWithCount / ((endTime - startTime) / 1000.0);
        DecimalFormat df = new DecimalFormat("0.00000000");
        String outStr = "performance - send tx tese case ...... success , result : "
                + df.format(tps) + " TPS";
        System.out.println(outStr);
    }

    public static void main(String[] args) throws Exception {
        int sendcount = 20;
        int threadcount = 1;
        boolean isEd25519AndBlake2b = false;

        System.out.println(
                "sendCount: " + sendcount + " threadCount: " + threadcount
                        + " isEd25519AndBlake2b: " + isEd25519AndBlake2b);
        AdvanceTransactionTest advanceTxTest = new AdvanceTransactionTest(
                sendcount, threadcount, isEd25519AndBlake2b);
        advanceTxTest.runContract();
        System.out.println("Performance - test case complete");
    }
}
