package com.bloxbean.contracts;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Initializable;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.AionSet;

import java.math.BigInteger;
import java.util.Map;
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

    private static long minBlockNoDelay = 8640; //10sec per block. 24hr delay. 6 x 60 x 24

    private static BigInteger operatorThresholdBalance = new BigInteger("1000000000000000000"); //1 AION
    private static BigInteger operatorTransferBalance = new BigInteger("10000000000000000000"); //10    AION

    private static BigInteger ONETIME_TRANSFER_AMOUNT = new BigInteger("1000000000000000000"); //1 Aion

    private static Set<Address> operators;

    private static Map<Address, AccountDetails> recipients;

    static {
        recipients = new AionMap<>();
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
     * Transfer specified amount to the address. This method can only be called by a operator account. Ideally, this method
     * is called to credit newly generated account. The account registered through this operation can only request for topup
     * later. This method is called from the centralized server.
     * @param toAddress
     * @param amount
     */
    @Callable
    public static void registerAddress(Address toAddress, BigInteger amount) {
        onlyOperator();
        require(BigInteger.ZERO.compareTo(amount) == -1);

        //Check operator's balance. Topup if required
        if (Blockchain.getBalance(getCaller()).compareTo(operatorThresholdBalance) == -1) {
            //transfer
            Blockchain.call(getCaller(), operatorTransferBalance, new byte[0], getRemainingEnergy());
        }

        Result result = call(toAddress, amount, new byte[0], getRemainingEnergy());

        if (result.isSuccess()) {
            println("Transfer was successful. " + getBalance(toAddress) + " - " + getBalanceOfThisContract());

            //Registered the account
            AccountDetails accountDetails = new AccountDetails();
            accountDetails.lastRequestBlockNo = getBlockNumber();
            accountDetails.total = amount;

            recipients.put(toAddress, accountDetails);

            FaucetEvent.addressRegistered(toAddress);
        } else {
            println("Transfer failed to address : " + toAddress);
        }
    }

    /**
     * Get register recipients
     * @return
     */
    @Callable
    public static Address[] getRecipients() {

        Address[] addresses = new Address[recipients.size()];

        // Copying contents of s to arr[]
        int i = 0;
        for (Address a : recipients.keySet())
            addresses[i++] = a;

        return addresses;
    }

    /**
     * Called by an address to request for a topup
     */
    @Callable
    public static void topUp() {
        require(canRequest(getCaller()));
        require(recipients.get(getCaller()) != null);

        Result result = call(getCaller(), ONETIME_TRANSFER_AMOUNT, new byte[0], getRemainingEnergy());

        if (result.isSuccess()) {

            AccountDetails accountDetails = recipients.get(getCaller());
            accountDetails.lastRequestBlockNo = getBlockNumber();
            accountDetails.total = accountDetails.total.add(ONETIME_TRANSFER_AMOUNT);

            if(accountDetails.retryCount >= MAX_NO_OF_TRIES) //reset the counter
                accountDetails.retryCount = 1;
            else
                accountDetails.retryCount++;

            recipients.put(getCaller(), accountDetails);

            FaucetEvent.topup(getCaller(), ONETIME_TRANSFER_AMOUNT);

            println("Topup was successful. " + getBalance(getCaller()) + " - " + getBalanceOfThisContract());
        } else {
            println("Topup failed to address : " + getCaller());
        }
    }

    @Callable
    public static boolean canRequest(Address address) {
        AccountDetails accountDetails = (AccountDetails) recipients.get(address);

        if (accountDetails == null)
            return true;

        if (Blockchain.getBlockNumber() - accountDetails.lastRequestBlockNo >= minBlockNoDelay) {
            accountDetails.retryCount = 0; //reset retryCount
            return true;
        } else {
            if(accountDetails.retryCount < MAX_NO_OF_TRIES) //Can try MAX_NO_OF_TRIES with the time limit
                return true;
            else
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
     * Refund the remaining fund to owner's account and stop the contract.
     */
    @Callable
    public static void destruct() {
        Blockchain.selfDestruct(owner);
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
