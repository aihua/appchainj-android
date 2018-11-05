package org.nervos.appchain.protocol.admin;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.nervos.appchain.protocol.AppChainjService;
import org.nervos.appchain.protocol.admin.methods.response.NewAccountIdentifier;
import org.nervos.appchain.protocol.admin.methods.response.PersonalListAccounts;
import org.nervos.appchain.protocol.admin.methods.response.PersonalUnlockAccount;
import org.nervos.appchain.protocol.core.JsonRpc2_0AppChainj;
import org.nervos.appchain.protocol.core.Request;
import org.nervos.appchain.protocol.core.methods.request.Transaction;
import org.nervos.appchain.protocol.core.methods.response.AppSendTransaction;

/**
 * JSON-RPC 2.0 factory implementation for common Parity and Geth.
 */
public class JsonRpc2_0Admin extends JsonRpc2_0AppChainj implements Admin {

    public JsonRpc2_0Admin(AppChainjService appChainjService) {
        super(appChainjService);
    }
    
    @Override
    public Request<?, PersonalListAccounts> personalListAccounts() {
        return new Request<>(
                "personal_listAccounts",
                Collections.<String>emptyList(),
                appChainjService,
                PersonalListAccounts.class);
    }

    @Override
    public Request<?, NewAccountIdentifier> personalNewAccount(String password) {
        return new Request<>(
                "personal_newAccount",
                Arrays.asList(password),
                appChainjService,
                NewAccountIdentifier.class);
    }   

    @Override
    public Request<?, PersonalUnlockAccount> personalUnlockAccount(
            String accountId, String password,
            BigInteger duration) {
        List<Object> attributes = new ArrayList<>(3);
        attributes.add(accountId);
        attributes.add(password);
        
        if (duration != null) {
            // Parity has a bug where it won't support a duration
            // See https://github.com/ethcore/parity/issues/1215
            attributes.add(duration.longValue());
        } else {
            // we still need to include the null value, otherwise Parity rejects request
            attributes.add(null);
        }
        
        return new Request<>(
                "personal_unlockAccount",
                attributes,
                appChainjService,
                PersonalUnlockAccount.class);
    }
    
    @Override
    public Request<?, PersonalUnlockAccount> personalUnlockAccount(
            String accountId, String password) {
        
        return personalUnlockAccount(accountId, password, null);
    }
    
    @Override
    public Request<?, AppSendTransaction> personalSendTransaction(
            Transaction transaction, String passphrase) {
        return new Request<>(
                "personal_sendTransaction",
                Arrays.asList(transaction, passphrase),
                appChainjService,
                AppSendTransaction.class);
    }
    
}
