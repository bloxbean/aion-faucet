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

public class AionFaucetContract {

    @Initializable
    private static Address owner;

    private static long minBlockNoDelay = 8640; //10sec per block. 24hr delay. 6 x 60 x 24

    private static BigInteger operatorThresholdBalance = new BigInteger(String.valueOf("1000000000000000000")); //1 AION
    private static BigInteger operatorTransferBalance = new BigInteger(String.valueOf("10000000000000000000")); //10    AION

    private static Set<Address> operators;
    private static Map<Address, AccountDetails> recipients;

    static {
        recipients = new AionMap<>();
        operators = new AionSet<>();

        //Owner is also an operator
        // operators.add(owner);
    }

    public static class AccountDetails {
        private BigInteger total;
        private long lastRequestBlockNo;
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
     * Transfer specified amount to the address
     * @param toAddress
     * @param amount
     */
    @Callable
    public static void transfer(Address toAddress, long amount) {
        onlyOperator();
        require(amount > 0);

        //Check operator's balance. Topup if required
        if (Blockchain.getBalance(getCaller()).compareTo(operatorThresholdBalance) == -1) {
            //transfer
            Blockchain.call(getCaller(), operatorTransferBalance, new byte[0], getRemainingEnergy());
        }

        Result result = call(toAddress, BigInteger.valueOf(amount), new byte[0], getRemainingEnergy());

        if (result.isSuccess()) {
            println("Transfer was successful. " + getBalance(toAddress) + " - " + getBalanceOfThisContract());
        } else {
            println("Transfer failed to address : " + toAddress);
        }
    }

    @Callable
    public static boolean canRequest(Address address) {
        AccountDetails accountDetails = (AccountDetails) recipients.get(address);

        if (accountDetails == null)
            return true;

        if (Blockchain.getBlockNumber() - accountDetails.lastRequestBlockNo >= minBlockNoDelay)
            return true;
        else
            return false;
    }

    private static void onlyOperator() {
        require(operators.contains(getCaller()));
    }

    private static void onlyOwner() {
        require(owner.equals(getCaller()));
    }

}
