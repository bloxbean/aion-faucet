package com.bloxbean.contracts;

import avm.Address;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.embed.AvmRule;
import org.aion.avm.tooling.ABIUtil;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.AionSet;
import org.aion.types.AionAddress;
import org.aion.types.TransactionStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.math.BigInteger;

public class AionFaucetContractTest {
    @Rule
    public AvmRule avmRule = new AvmRule(true);

    private static long energyLimit = 2000000L;
    private static long energyPrice = 1L;

    private static BigInteger contractInitialalance = new BigInteger("500000000000000000000");

    private static Address owner = new Address(Helpers.hexStringToBytes("0xa0b5410b6ce75a2880d7e11e74f2b3832579f6813da41af59c931a0ea55414d4"));
    private static Address operator1 = new Address(Helpers.hexStringToBytes("0xa0baf37224a9eacde5c308161298afed14003d99ddb59bb784fc22a2d1d02fc8"));
    private static Address operator2 = new Address(Helpers.hexStringToBytes("0xa0caab7224a9eacde5c308161298afed14003d99ddb59bb784fc22a2d1d02f66"));
    private static Address dev1 = new Address(Helpers.hexStringToBytes("0xa0a0228985614e6368eacfaf01436ddb68c67389c2f5830c95da570594a42da3"));
    private static Address dev2 = new Address(Helpers.hexStringToBytes("0xa0c606b925377d66b087d406eceed90995e17eab443b3e6211cc2b54b9f89aa6"));

    //default address with balance
    private  Address from = avmRule.getPreminedAccount();

    private Address dappAddr;

    @Before
    public void deployDapp() {
        //deploy Dapp:
        // 1- get the Dapp byes to be used for the deploy transaction
        // 2- deploy the Dapp and get the address.

        //Deploy dapp as owner argument
        byte[] deploymentArgs = ABIUtil.encodeDeploymentArguments(owner);
        byte[] dapp = avmRule.getDappBytes(AionFaucetContract.class, deploymentArgs, AionMap.class, AionSet.class);
        dappAddr = avmRule.deploy(from, BigInteger.ZERO, dapp).getDappAddress();

        //add sufficient aion to the default account
        avmRule.kernel.adjustBalance(new AionAddress(from.toByteArray()), new BigInteger("6000000000000000000000"));

        AvmRule.ResultWrapper result = avmRule.balanceTransfer(from, dappAddr, contractInitialalance, energyLimit, energyPrice);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        //Adjust owner's balance
        avmRule.kernel.adjustBalance(new AionAddress(owner.toByteArray()), new BigInteger("100000000000000000"));
    }

    @Test
    public void whenOwnerAddOperatorThenSuccess() {
        byte[] txData = ABIUtil.encodeMethodArguments("addOperator", operator1);
        AvmRule.ResultWrapper result = avmRule.call(owner, dappAddr, BigInteger.ZERO, txData);

        // getReceiptStatus() checks the status of the transaction execution
        TransactionStatus status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());

        txData = ABIUtil.encodeMethodArguments("getOperators");
        result = avmRule.call(from, dappAddr, BigInteger.ZERO, txData);
        TransactionStatus status1 = result.getReceiptStatus();
        Assert.assertTrue(status1.isSuccess());

        byte[] data = result.getTransactionResult().copyOfTransactionOutput().get();

        Address[] addresses = (Address [])result.getDecodedReturnData();

        Assert.assertEquals(operator1, addresses[0]);

    }

    @Test
    public void whenANonOwnerAddOperatorThenFail() {
        Address nonOwner = new Address(Helpers.hexStringToBytes("0xa0cccc0b6ce75a2880d7e11e74f2b3832579f6813da41af59c931a0ea5541ddd"));
        allocateBalance(nonOwner, "100000000000000000"); //allocate some balance to nonowner

        byte[] txData = ABIUtil.encodeMethodArguments("addOperator", operator1);
        AvmRule.ResultWrapper result = avmRule.call(nonOwner, dappAddr, BigInteger.ZERO, txData);

        // getReceiptStatus() checks the status of the transaction execution
        TransactionStatus status = result.getReceiptStatus();
        Assert.assertTrue(status.isFailed());
    }

    @Test
    public void whenRemoveOperatorThenOperatorNotFoundInList() {
        addOperator(operator1);
        addOperator(operator2);

        Address[] operators = getOperators();

        Assert.assertEquals(2, operators.length);

        //remove
        removeOperator(operator1);
        operators = getOperators();

        Assert.assertEquals(1, operators.length);
        Assert.assertEquals(operator2, operators[0]);
    }

    @Test
    public void whenAddOperatorThenOperatorAssignedWithSomeBalance() {
        BigInteger balance1 = getBalance(operator1);
        BigInteger balance2 = getBalance(operator2);

        Assert.assertEquals(BigInteger.ZERO, balance1);
        Assert.assertEquals(BigInteger.ZERO, balance2);

        addOperator(operator1);
        addOperator(operator2);

        balance1 = getBalance(operator1);
        balance2 = getBalance(operator2);

        BigInteger opExpectedBalance = new BigInteger(String.valueOf("10000000000000000000"));
        Assert.assertEquals(opExpectedBalance, balance1);
        Assert.assertEquals(opExpectedBalance, balance2);

    }

    private void addOperator(Address operator) {
        byte[] txData = ABIUtil.encodeMethodArguments("addOperator", operator);
        AvmRule.ResultWrapper result = avmRule.call(owner, dappAddr, BigInteger.ZERO, txData);

        // getReceiptStatus() checks the status of the transaction execution
        TransactionStatus status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
    }

    private void removeOperator(Address operator) {
        //delete operator
        byte[] txData = ABIUtil.encodeMethodArguments("removeOperator", operator);
        AvmRule.ResultWrapper result = avmRule.call(owner, dappAddr, BigInteger.ZERO, txData);

        TransactionStatus status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
    }

    private BigInteger getBalance(Address address) {
        return avmRule.kernel.getBalance(new AionAddress(address.toByteArray()));
    }

    private Address[] getOperators() {
        byte[] txData = ABIUtil.encodeMethodArguments("getOperators");
        AvmRule.ResultWrapper result = avmRule.call(from, dappAddr, BigInteger.ZERO, txData);
        TransactionStatus status1 = result.getReceiptStatus();
        Assert.assertTrue(status1.isSuccess());

        byte[] data = result.getTransactionResult().copyOfTransactionOutput().get();

        Address[] addresses = (Address [])result.getDecodedReturnData();

        return addresses;
    }

    private void allocateBalance(Address address, String balance) {
        avmRule.kernel.adjustBalance(new AionAddress(address.toByteArray()), new BigInteger(balance));
    }

}

