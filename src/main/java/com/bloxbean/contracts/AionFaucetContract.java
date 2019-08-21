package com.bloxbean.contracts;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Initializable;
import org.aion.avm.userlib.AionBuffer;
import org.aion.avm.userlib.AionSet;

import java.math.BigInteger;
import java.util.Set;

import static avm.Blockchain.*;

/**
 * Aion Faucet contract. This contract is used to topup developer account on testnet network.
 * Owner: Owner of this contract
 * Operator: Operator's account is used to register a new account with some minimum balance
 */
public class AionFaucetContract {

    public static final int MAX_NO_OF_TRIES = 3;
    @Initializable
    private static Address owner;

    private static BigInteger ONE_AION = new BigInteger("1000000000000000000"); //1 Aion

    private static long minBlockNoDelay = 8640; //10sec per block. 24hr delay. 6 x 60 x 24

    private static BigInteger operatorThresholdBalance = new BigInteger("1000000000000000000"); //1 AION
    private static BigInteger operatorTransferBalance = new BigInteger("10000000000000000000"); //10    AION

    //Topup amount for new account
    private static BigInteger initialTopupAmount = new BigInteger("500000000000000000"); //0.5 Aion

    //Regular topup amount
    private static BigInteger topupAmount = ONE_AION; //1 Aion

    //An event will be thrown if contract's balance is less than contractMinimumBalance
    private static BigInteger contractMinimumBalance = new BigInteger("10000000000000000000"); //10 Aion

    private static Set<Address> operators;
    private static long totalRecipients;

    static {
        operators = new AionSet<>();
    }

    public static class AccountDetails {
        private BigInteger total;
        private long lastRequestBlockNo;
        private int retryCount; //Total no of request in an allowed perid
    }

    /**
     * Add a new operator account to the contract. If operator account doesn't has minimum threshold balance to operate,
     * transfer a default operator balance to the newly added operator account.
     * Only owner can call this method.
     * @param address
     */
    @Callable
    public static void addOperator(Address address) {

        onlyOwner();
        require(address != null);
        require(!operators.contains(address));

        operators.add(address);
        if (Blockchain.getBalance(address).compareTo(operatorThresholdBalance) == -1) {
            //transfer
            Blockchain.call(address, operatorTransferBalance, new byte[0], getRemainingEnergy());
        }

        FaucetEvent.operatorAdded(address);
    }

    /**
     * Remove an operator from operators list
     * @param address Operator address
     */
    @Callable
    public static void removeOperator(Address address) {
        onlyOwner();
        require(address != null);
        require(operators.contains(address));

        operators.remove(address);

        FaucetEvent.operatorRemoved(address);
    }

    /**
     * Get list of current operators
     * @return list of operators
     */
    @Callable
    public static Address[] getOperators() {

        Address[] addresses = new Address[operators.size()];

        // Copying contents of s to arr[]
        int i = 0;
        for (Address a : operators)
            addresses[i++] = a;

        return addresses;
    }

    /**
     * Transfer specified initialTopupAmount to the address. This method can only be called by a operator account. Ideally, this method
     * is called to credit newly generated account. The account registered through this operation can only request for topup
     * later. This method is called from the centralized server.
     * @param toAddress
     */
    @Callable
    public static void registerAddress(Address toAddress) {
        onlyOperator();
        require(BigInteger.ZERO.compareTo(initialTopupAmount) == -1);

        //Check operator's balance. Topup if required
        if (Blockchain.getBalance(getCaller()).compareTo(operatorThresholdBalance) == -1) {
            //transfer
            Blockchain.call(getCaller(), operatorTransferBalance, new byte[0], getRemainingEnergy());
        }

        Result result = call(toAddress, initialTopupAmount, new byte[0], getRemainingEnergy());

        if (result.isSuccess()) {
            println("Transfer was successful. " + getBalance(toAddress) + " - " + getBalanceOfThisContract());

            //Registered the account
            AccountDetails accountDetails = new AccountDetails();
            accountDetails.lastRequestBlockNo = getBlockNumber();
            accountDetails.total = initialTopupAmount;

            addRecipientDetailsToStorage(toAddress, accountDetails);

            totalRecipients++;

            FaucetEvent.addressRegistered(toAddress);
        } else {
            println("Transfer failed to address : " + toAddress);
        }
    }

    @Callable
    public static long getTotalRecipients() {
        return totalRecipients;
    }

    /**
     * Check if an address is already registered
     * @param address
     * @return
     */
    @Callable
    public static boolean isAddressRegistered(Address address) {
        AccountDetails accountDetails = getRecipientDetailsFromStorage(address);
        if(accountDetails != null)
            return true;
        else
            return false;
    }

    /**
     * Get recipient's retry count in a day
     * @param address
     * @return
     */
    @Callable
    public static int getRecipientRetryCount(Address address) {
        AccountDetails accountDetails = getRecipientDetailsFromStorage(address);

        if(accountDetails != null) {
            return accountDetails.retryCount;
        } else {
            return -1;
        }
    }

    /**
     * Get total aion claimed by the recipient
     * @param address
     * @return
     */
    @Callable
    public static BigInteger getRecipientTotal(Address address) {
        AccountDetails accountDetails = getRecipientDetailsFromStorage(address);

        if(accountDetails != null) {
            return accountDetails.total;
        } else {
            return BigInteger.ZERO;
        }
    }

    /**
     * Get recipient's last requested block number
     * @param address
     * @return
     */
    @Callable
    public static long getRecipientLastRequestBlockNo(Address address) {
        AccountDetails accountDetails = getRecipientDetailsFromStorage(address);

        if(accountDetails != null) {
            return accountDetails.lastRequestBlockNo;
        } else {
            return -1;
        }
    }

    /**
     * Called by an address to request for a topup
     */
    @Callable
    public static void topUp() {
        require(canRequest(getCaller()));
        require(isRegistered());

        Result result = call(getCaller(), topupAmount, new byte[0], getRemainingEnergy());

        if (result.isSuccess()) {

            AccountDetails accountDetails = getRecipientDetailsFromStorage(getCaller());
            accountDetails.lastRequestBlockNo = getBlockNumber();
            accountDetails.total = accountDetails.total.add(topupAmount);

            if(accountDetails.retryCount >= MAX_NO_OF_TRIES) //reset the counter
                accountDetails.retryCount = 1;
            else
                accountDetails.retryCount++;

            addRecipientDetailsToStorage(getCaller(), accountDetails);

            FaucetEvent.topup(getCaller(), topupAmount);

            println("Topup was successful. " + getBalance(getCaller()) + " - " + getBalanceOfThisContract());
        } else {
            println("Topup failed to address : " + getCaller());
        }

        BigInteger contractBalance = Blockchain.getBalanceOfThisContract();
        if(contractBalance != null && contractBalance.compareTo(contractMinimumBalance) == -1) {
            FaucetEvent.balanceBelowMinimum(contractBalance);
        }
    }

    @Callable
    public static boolean canRequest(Address address) {
        AccountDetails accountDetails = getRecipientDetailsFromStorage(address);

        if (accountDetails == null)
            return true;

        if (Blockchain.getBlockNumber() - accountDetails.lastRequestBlockNo >= minBlockNoDelay) {
            accountDetails.retryCount = 0; //reset retryCount

            //Update account details with new retrycount
            addRecipientDetailsToStorage(address, accountDetails);
            return true;
        } else {
            if(accountDetails.retryCount < MAX_NO_OF_TRIES) //Can try MAX_NO_OF_TRIES with the time limit
                return true;
            else {
//                FaucetEvent.maximumDailyRetryLimitReached(address, String.valueOf(accountDetails.retryCount));
                return false;
            }
        }
    }

    private static boolean isRegistered() {
        boolean isRegistered = getRecipientDetailsFromStorage(getCaller()) != null;

        if(isRegistered) {
            return true;
        } else {
//            FaucetEvent.addressNotRegistered();
            return false;
        }
    }

    @Callable
    public static void setMinBlockDelay(long blockDelay) {
        onlyOwner();
        minBlockNoDelay = blockDelay;
    }

    @Callable
    public static long getMinBlockDelay() {
        return minBlockNoDelay;
    }

    @Callable
    public static Address getOwner() {
        return owner;
    }

    /**
     * Get predefined topup amount in nAmp
     * @return Topup amount per topup
     */
    @Callable
    public static BigInteger getTopupAmount() {
        return topupAmount;
    }

    /**
     * Set topup amount in Aion
     * @param amount in Aion
     */
    @Callable
    public static void setTopupAmount(BigInteger amount) {
        onlyOwner();
        topupAmount = ONE_AION.multiply(amount);
    }

    /**
     * Get the initial topup amount for a new account.
     * @return Initial topup amount in nAmp
     */
    @Callable
    public static BigInteger getInitialTopupAmount() {
        return initialTopupAmount;
    }

    /**
     * Set initial topup amount,
     * @param amount in Aion
     */
    @Callable
    public static void setInitialTopupAmount(BigInteger amount) {
        onlyOwner();
        initialTopupAmount = ONE_AION.multiply(amount);
    }

    /**
     * Get contract's minimum balance. Below this balance, an event will be published
     * Default: 10 Aion
     * @return contractMinimumBalance in nAmp
     */
    @Callable
    public static BigInteger getContractMinimumBalance() {
        return contractMinimumBalance;
    }

    /**
     * Set contract's minimum balance.
     * @param amount in Aion
     */
    @Callable
    public static void setContractMinimumBalance(BigInteger amount) {
        onlyOwner();
        contractMinimumBalance = ONE_AION.multiply(amount);
    }

    /**
     * Refund the remaining fund to owner's account and stop the contract.
     */
    @Callable
    public static void destruct() {
        onlyOwner();
        Blockchain.selfDestruct(owner);
    }

    private static void addRecipientDetailsToStorage(Address address, AccountDetails accountDetails) {
        putStorage(getRecipientKeyBytes(address), getRecipientAccountDetailsBytes(accountDetails));
    }

    private static AccountDetails getRecipientDetailsFromStorage(Address address) {
        byte[] bytes = getStorage(getRecipientKeyBytes(address));

        if(bytes == null || bytes.length == 0) {
            return null;
        }

        AionBuffer buffer = AionBuffer.wrap(bytes);

        int retryCount = buffer.getInt();
        BigInteger total = buffer.get32ByteInt();
        long lastRequestBlockNo = buffer.getLong();

        AccountDetails accountDetails = new AccountDetails();
        accountDetails.retryCount = retryCount;
        accountDetails.total = total;
        accountDetails.lastRequestBlockNo = lastRequestBlockNo;

        return accountDetails;
    }

    private static byte[] getRecipientKeyBytes(Address address) {
        return AionBuffer.allocate(Address.LENGTH)
                .putAddress(address)
                .getArray();
    }

    private static byte[] getRecipientAccountDetailsBytes(AccountDetails accountDetails) {
        AionBuffer aionBuffer = AionBuffer.allocate(Integer.BYTES + 32 + Long.BYTES);

        return aionBuffer.putInt(accountDetails.retryCount)
                .put32ByteInt(accountDetails.total)
                .putLong(accountDetails.lastRequestBlockNo)
                .getArray();
    }

    /**
     * Check onlyOperator
     */
    private static void onlyOperator() {
        require(operators.contains(getCaller()));
    }

    /**
     * Check only owner
     */
    private static void onlyOwner() {
        require(owner.equals(getCaller()));
    }

}
