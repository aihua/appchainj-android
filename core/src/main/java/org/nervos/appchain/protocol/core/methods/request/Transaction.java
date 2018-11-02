package org.nervos.appchain.protocol.core.methods.request;

import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.protobuf.ByteString;
import org.abstractj.kalium.crypto.Hash;
import org.abstractj.kalium.keys.SigningKey;

import org.nervos.appchain.crypto.Credentials;
import org.nervos.appchain.crypto.ECKeyPair;
import org.nervos.appchain.crypto.Sign;
import org.nervos.appchain.protobuf.Blockchain;
import org.nervos.appchain.protobuf.Blockchain.Crypto;
import org.nervos.appchain.protobuf.ConvertStrByte;
import org.nervos.appchain.utils.Numeric;

import static org.abstractj.kalium.encoders.Encoder.HEX;

/**
 * Transaction request object used the below methods.
 * <ol>
 * <li>eth_call</li>
 * <li>eth_sendTransaction</li>
 * <li>eth_estimateGas</li>
 * </ol>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Transaction {

    private String to;
    private BigInteger nonce;  // nonce field is not present on eth_call/eth_estimateGas
    private long quota;  // gas
    private long validUntilBlock;
    private int version = 0;
    private String data;
    private String value;
    private int chainId;
    private final Hash hash = new Hash();
    private static final BigInteger MAX_VALUE
            = new BigInteger(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16);

    public Transaction(
            String to, BigInteger nonce, long quota, long validUntilBlock,
            int version, int chainId, String value, String data) {
        this.to = to;
        this.nonce = nonce;
        this.quota = quota;
        this.version = version;
        this.validUntilBlock = validUntilBlock;
        this.chainId = chainId;
        this.value = value;

        if (data != null) {
            this.data = Numeric.prependHexPrefix(data);
        }

        if (value.length() < 32) {
            if (value.matches("0[xX][0-9a-fA-F]+")) {
                this.value = value.substring(2);
            } else {
                this.value = processValue(value);
            }
        }

    }

    public static String processValue(String value) {
        String result = "";
        if (value.matches("0[xX][0-9a-fA-F]+")) {
            result = value.substring(2);
        } else if (value == null || value.equals("")) {
            result = "0";
        } else {
            result = new BigInteger(value).toString(16);
        }

        BigInteger valueBigInt = new BigInteger(result, 16);

        if (Transaction.MAX_VALUE.compareTo(valueBigInt) > 0) {
            return result;
        } else {
            System.out.println("Value you input is out of bound");
            System.out.println("Value is set as 0");
            return "0";
        }
    }

    public static Transaction createContractTransaction(
            BigInteger nonce, long quota, long validUntilBlock,
            int version, int chainId, String value, String init) {
        return new Transaction("", nonce, quota, validUntilBlock, version, chainId, value, init);
    }

    public static Transaction createFunctionCallTransaction(
            String to, BigInteger nonce, long quota, long validUntilBlock,
            int version, int chainId, String value, String data) {
        return new Transaction(to, nonce, quota, validUntilBlock, version, chainId, value, data);
    }

    public static Transaction createFunctionCallTransaction(
            String to, BigInteger nonce, long quota, long validUntilBlock,
            int version, int chainId, String value,  byte[] data) {

        return new Transaction(
                to, nonce, quota, validUntilBlock, version, chainId, value, new String(data));
    }

    public String getTo() {
        return to;
    }

    public String getNonce() {
        return convert(nonce);
    }

    public long getQuota() {
        return quota;
    }

    public long get_valid_until_block() {
        return validUntilBlock;
    }

    public int getVersion() {
        return version;
    }

    public String getData() {
        return data;
    }

    public int getChainId() {
        return chainId;
    }

    public String getValue() {
        return value;
    }

    private static String convert(BigInteger value) {
        if (value != null) {
            return Numeric.cleanHexPrefix(Numeric.encodeQuantity(value));
        } else {
            return null;  // we don't want the field to be encoded if not present
        }
    }

    public String sign(String privateKey, boolean isEd25519AndBlake2b, boolean isByteArray) {
        Blockchain.Transaction.Builder builder = Blockchain.Transaction.newBuilder();

        byte[] strbyte;
        if (isByteArray) {
            strbyte = getData().getBytes();
        } else {
            strbyte = ConvertStrByte.hexStringToBytes(Numeric.cleanHexPrefix(getData()));
        }
        ByteString bdata = ByteString.copyFrom(strbyte);

        byte[] byteValue = ConvertStrByte.hexStringToBytes(Numeric.cleanHexPrefix(getValue()), 256);
        ByteString bvalue = ByteString.copyFrom(byteValue);

        builder.setData(bdata);
        builder.setNonce(getNonce());
        builder.setTo(getTo());
        builder.setValidUntilBlock(get_valid_until_block());
        builder.setVersion(getVersion());
        builder.setQuota(getQuota());
        builder.setChainId(getChainId());
        builder.setValue(bvalue);
        Blockchain.Transaction tx = builder.build();

        byte[] sig;
        if (isEd25519AndBlake2b) {
            byte[] message = hash.blake2(
                    tx.toByteArray(), "CryptapeCryptape".getBytes(), null, null);
            SigningKey key = new SigningKey(privateKey, HEX);
            byte[] pk = key.getVerifyKey().toBytes();
            byte[] signature = key.sign(message);
            sig = new byte[signature.length + pk.length];
            System.arraycopy(signature, 0, sig, 0, signature.length);
            System.arraycopy(pk, 0, sig, signature.length, pk.length);
        } else {
            Credentials credentials = Credentials.create(privateKey);
            ECKeyPair keyPair = credentials.getEcKeyPair();
            Sign.SignatureData signatureData = Sign.signMessage(tx.toByteArray(), keyPair);
            sig = signatureData.get_signature();
        }

        Blockchain.UnverifiedTransaction.Builder builder1 =
                Blockchain.UnverifiedTransaction.newBuilder();
        builder1.setTransaction(tx);
        builder1.setSignature(ByteString.copyFrom(sig));
        builder1.setCrypto(Crypto.SECP);
        Blockchain.UnverifiedTransaction utx = builder1.build();
        String txStr = ConvertStrByte.bytesToHexString(utx.toByteArray());

        return Numeric.prependHexPrefix(txStr);
    }

    // just used to secp256k1
    public String sign(Credentials credentials) {
        Blockchain.Transaction.Builder builder = Blockchain.Transaction.newBuilder();
        byte[] strbyte = ConvertStrByte.hexStringToBytes(
                Numeric.cleanHexPrefix(getData()));
        ByteString bdata = ByteString.copyFrom(strbyte);

        byte[] byteValue = ConvertStrByte.hexStringToBytes(
                Numeric.cleanHexPrefix(getValue()), 256);
        ByteString bvalue = ByteString.copyFrom(byteValue);

        builder.setData(bdata);
        builder.setNonce(getNonce());
        builder.setTo(getTo());
        builder.setValidUntilBlock(get_valid_until_block());
        builder.setQuota(getQuota());
        builder.setVersion(getVersion());
        builder.setChainId(getChainId());
        builder.setValue(bvalue);
        Blockchain.Transaction tx = builder.build();

        ECKeyPair keyPair = credentials.getEcKeyPair();
        Sign.SignatureData signatureData = Sign.signMessage(tx.toByteArray(), keyPair);
        byte[] sig = signatureData.get_signature();

        Blockchain.UnverifiedTransaction.Builder builder1 =
                Blockchain.UnverifiedTransaction.newBuilder();
        builder1.setTransaction(tx);
        builder1.setSignature(ByteString.copyFrom(sig));
        builder1.setCrypto(Crypto.SECP);
        Blockchain.UnverifiedTransaction utx = builder1.build();
        String txStr = ConvertStrByte.bytesToHexString(utx.toByteArray());

        return Numeric.prependHexPrefix(txStr);
    }
}
