package org.inchain.store;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.inchain.Configure;
import org.inchain.account.AccountBody;
import org.inchain.account.Address;
import org.inchain.consensus.ConsensusModel;
import org.inchain.consensus.ConsensusPool;
import org.inchain.core.Assets;
import org.inchain.core.Coin;
import org.inchain.core.ViolationEvidence;
import org.inchain.crypto.Sha256Hash;
import org.inchain.transaction.Transaction;
import org.inchain.transaction.business.*;
import org.inchain.utils.Utils;
import org.iq80.leveldb.DBIterator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * 链状态查询提供服务，存放的是所有的未花费交易，以及共识节点
 * @author ln
 *
 */
@Repository
public class ChainstateStoreProvider extends BaseStoreProvider {
	
	private Lock consensusLocker = new ReentrantLock();
	private Lock revokeLock  = new ReentrantLock();
	private Lock assetsLock = new ReentrantLock();
	
	@Autowired
	private BlockStoreProvider blockStoreProvider;
	@Autowired
	private ConsensusPool consensusPool;

	protected ChainstateStoreProvider() {
		this(Configure.DATA_CHAINSTATE);
	}
	
	protected ChainstateStoreProvider(String dir) {
		this(dir, -1, -1);
	}
	protected ChainstateStoreProvider(String dir, long leveldbReadCache,
			int leveldbWriteCache) {
		super(dir, leveldbReadCache, leveldbWriteCache);
	}

	@Override
	protected byte[] toByte(Store store) {
		if(store == null) {
			throw new NullPointerException("transaction is null");
		}
		TransactionStore transactionStore = (TransactionStore) store;
		
		Transaction transaction = transactionStore.getTransaction();
		if(transaction == null) {
			throw new NullPointerException("transaction is null");
		}
		return transaction.baseSerialize();
	}

	@Override
	protected Store pase(byte[] content) {
		if(content == null) {
			throw new NullPointerException("transaction content is null");
		}
		Transaction transaction = new Transaction(network, content);
		TransactionStore store = new TransactionStore(network, transaction);
		return store;
	}
	
	/**
	 * 获取账户的公钥
	 * 如果是认证账户，则获取的是最新的公钥
	 * @param hash160 账户的hash160
	 * @return byte[][] 普通账户返回1个，认证账户前两个为管理公钥、后两个为交易公钥，当没有查到时返回null
	 */
	public byte[][] getAccountPubkeys(byte[] hash160) {
		AccountStore store = getAccountInfo(hash160);
		if(store == null) {
			return null;
		} else {
			return store.getPubkeys();
		}
	}

	/**
	 * 获取账户信息
	 * @param hash160
	 * @return AccountStore
	 */
	public AccountStore getAccountInfo(byte[] hash160) {
		byte[] accountBytes = getBytes(hash160);
		if(accountBytes == null) {
			return null;
		}
		AccountStore store = new AccountStore(network, accountBytes);
		return store;
	}
	
	/**
	 * 保存账户信息
	 * @param accountInfo
	 * @return boolean
	 */
	public boolean saveAccountInfo(AccountStore accountInfo) {
		try {
			put(accountInfo.getHash160(), accountInfo.baseSerialize());
			return true;
		} catch (Exception e) {
			log.error("保存账户信息出错：", e);
			return false;
		}
	}
	
	/**
	 * 认证商家添加子账户
	 * @param relevancSubAccountTx
	 * @return boolean
	 */
	public boolean addSubAccount(RelevanceSubAccountTransaction relevancSubAccountTx) {
		byte[] subAccountKey = new byte[22];
		subAccountKey[0] = 0;
		subAccountKey[1] = 1;
		System.arraycopy(relevancSubAccountTx.getHash160(), 0, subAccountKey, 2, Address.LENGTH);
		
		byte[] subAccounts = getBytes(subAccountKey);
		if(subAccounts == null) {
			subAccounts = new byte[0];
		}
		byte[] newSubAccounts = new byte[subAccounts.length + Address.HASH_LENGTH + Sha256Hash.LENGTH];
		System.arraycopy(subAccounts, 0, newSubAccounts, 0, subAccounts.length);
		System.arraycopy(relevancSubAccountTx.getRelevanceHashs(), 0, newSubAccounts, subAccounts.length, Address.HASH_LENGTH);
		System.arraycopy(relevancSubAccountTx.getHash().getBytes(), 0, newSubAccounts, subAccounts.length + Address.HASH_LENGTH, Sha256Hash.LENGTH);
		put(subAccountKey, newSubAccounts);
		return true;
	}
	
	/**
	 * 撤销认证商家添加的子账户
	 * @param relevancSubAccountTx
	 * @return boolean
	 */
	public boolean revokedAddSubAccount(RelevanceSubAccountTransaction relevancSubAccountTx) {
		byte[] subAccountKey = new byte[22];
		subAccountKey[0] = 0;
		subAccountKey[1] = 1;
		System.arraycopy(relevancSubAccountTx.getHash160(), 0, subAccountKey, 2, Address.LENGTH);
		
		byte[] subAccounts = getBytes(subAccountKey);
		
		byte[] newSubAccounts = new byte[subAccounts.length - (Address.HASH_LENGTH + Sha256Hash.LENGTH)];
		
		//找出位置在哪里
		//判断在列表里面才更新，否则就被清空了
		for (int j = 0; j < subAccounts.length; j += (Address.HASH_LENGTH + Sha256Hash.LENGTH)) {
			byte[] addressHashsTemp = Arrays.copyOfRange(subAccounts, j, j + Address.HASH_LENGTH);
			if(Arrays.equals(addressHashsTemp, relevancSubAccountTx.getRelevanceHashs())) {
				System.arraycopy(subAccounts, 0, newSubAccounts, 0, j);
				int newIndex = j + Address.HASH_LENGTH + Sha256Hash.LENGTH;
				if(newIndex < subAccounts.length) {
					System.arraycopy(subAccounts, newIndex, newSubAccounts, j, subAccounts.length - newIndex);
				}
				put(subAccountKey, newSubAccounts);
				return true;
			}
		}
		return false;
	}
	
	/**
	 * 认证商家删除子账户
	 * @param removeSubAccountTx
	 * @return boolean
	 */
	public boolean removeSubAccount(RemoveSubAccountTransaction removeSubAccountTx) {
		byte[] subAccountKey = new byte[22];
		subAccountKey[0] = 0;
		subAccountKey[1] = 1;
		System.arraycopy(removeSubAccountTx.getHash160(), 0, subAccountKey, 2, Address.LENGTH);
		
		byte[] subAccounts = getBytes(subAccountKey);
		
		byte[] newSubAccounts = new byte[subAccounts.length - (Address.HASH_LENGTH + Sha256Hash.LENGTH)];
		
		//找出位置在哪里
		//判断在列表里面才更新，否则就被清空了
		for (int j = 0; j < subAccounts.length; j += (Address.HASH_LENGTH + Sha256Hash.LENGTH)) {
			byte[] addressHashsTemp = Arrays.copyOfRange(subAccounts, j, j + Address.HASH_LENGTH);
			byte[] txhash = Arrays.copyOfRange(subAccounts, j + Address.HASH_LENGTH, j + Address.HASH_LENGTH + Sha256Hash.LENGTH);
			if(Arrays.equals(addressHashsTemp, removeSubAccountTx.getRelevanceHashs()) && Arrays.equals(txhash, removeSubAccountTx.getTxhash().getBytes())) {
				System.arraycopy(subAccounts, 0, newSubAccounts, 0, j);
				int newIndex = j + Address.HASH_LENGTH + Sha256Hash.LENGTH;
				if(newIndex < subAccounts.length) {
					System.arraycopy(subAccounts, newIndex, newSubAccounts, j, subAccounts.length - newIndex);
				}
				put(subAccountKey, newSubAccounts);
				return true;
			}
		}
		return false;
	}
	
	/**
	 * 撤销认证商家删除子账户
	 * @param removeSubAccountTx
	 * @return boolean
	 */
	public boolean revokedRemoveSubAccount(RemoveSubAccountTransaction removeSubAccountTx) {
		byte[] subAccountKey = new byte[22];
		subAccountKey[0] = 0;
		subAccountKey[1] = 1;
		System.arraycopy(removeSubAccountTx.getHash160(), 0, subAccountKey, 2, Address.LENGTH);
		
		byte[] subAccounts = getBytes(subAccountKey);
		if(subAccounts == null) {
			subAccounts = new byte[0];
		}
		byte[] newSubAccounts = new byte[subAccounts.length + Address.HASH_LENGTH + Sha256Hash.LENGTH];
		System.arraycopy(subAccounts, 0, newSubAccounts, 0, subAccounts.length);
		System.arraycopy(removeSubAccountTx.getRelevanceHashs(), 0, newSubAccounts, subAccounts.length, Address.HASH_LENGTH);
		System.arraycopy(removeSubAccountTx.getTxhash().getBytes(), 0, newSubAccounts, subAccounts.length + Address.HASH_LENGTH, Sha256Hash.LENGTH);
		put(subAccountKey, newSubAccounts);
		return true;
	}
	
	/**
	 * 获取认证账户的子账户列表
	 * @param certHash160
	 * @return List<RelevanceSubAccountTransaction>
	 */
	public List<RelevanceSubAccountTransaction> getSubAccountList(byte[] certHash160) {
		List<RelevanceSubAccountTransaction> list = new ArrayList<RelevanceSubAccountTransaction>();
		
		byte[] subAccountKey = new byte[22];
		subAccountKey[0] = 0;
		subAccountKey[1] = 1;
		System.arraycopy(certHash160, 0, subAccountKey, 2, Address.LENGTH);
		
		byte[] subAccounts = getBytes(subAccountKey);
		if(subAccounts == null || subAccounts.length == 0) {
			return list;
		}
		
		for (int j = 0; j < subAccounts.length; j += (Address.HASH_LENGTH + Sha256Hash.LENGTH)) {
			Sha256Hash txHash = Sha256Hash.wrap(Arrays.copyOfRange(subAccounts, j + Address.HASH_LENGTH, j + Address.HASH_LENGTH + Sha256Hash.LENGTH));
			
			TransactionStore txs = blockStoreProvider.getTransaction(txHash.getBytes());
			if(txs == null) {
				continue;
			}
			RelevanceSubAccountTransaction rst = (RelevanceSubAccountTransaction) txs.getTransaction();
			list.add(rst);
		}
		return list;
	}
	
	/**
	 * 获取认证账户的子账户数量
	 * @param certHash160
	 * @return int
	 */
	public int getSubAccountCount(byte[] certHash160) {
		byte[] subAccountKey = new byte[22];
		subAccountKey[0] = 0;
		subAccountKey[1] = 1;
		System.arraycopy(certHash160, 0, subAccountKey, 2, Address.LENGTH);
		
		byte[] subAccounts = getBytes(subAccountKey);
		if(subAccounts == null || subAccounts.length == 0) {
			return 0;
		}
		return (int)(subAccounts.length / (Address.HASH_LENGTH + Sha256Hash.LENGTH));
	}
	
	/**
	 * 检查是否是认证账户的子账户
	 * @param certHash160
	 * @param addressHashs
	 * @return Sha256Hash 返回商家添加子账户的交易id，有可能返回null
	 */
	public Sha256Hash checkIsSubAccount(byte[] certHash160, byte[] addressHashs) {
		byte[] subAccountKey = new byte[22];
		subAccountKey[0] = 0;
		subAccountKey[1] = 1;
		System.arraycopy(certHash160, 0, subAccountKey, 2, Address.LENGTH);
		
		byte[] subAccounts = getBytes(subAccountKey);
		if(subAccounts == null || subAccounts.length == 0) {
			return null;
		}
		for (int j = 0; j < subAccounts.length; j += (Address.HASH_LENGTH + Sha256Hash.LENGTH)) {
			byte[] addressHashsTemp = Arrays.copyOfRange(subAccounts, j, j + Address.HASH_LENGTH);
			if(Arrays.equals(addressHashsTemp, addressHashs)) {
				return Sha256Hash.wrap(Arrays.copyOfRange(subAccounts, j + Address.HASH_LENGTH, j + Address.HASH_LENGTH + Sha256Hash.LENGTH));
			}
		}
		return null;
	}
	
	/**
	 * 通过别名查询账户信息
	 * @param alias
	 * @return AccountStore
	 */
	public AccountStore getAccountInfoByAlias(byte[] alias) {
		byte[] hash160 = getAccountHash160ByAlias(alias);
		if(hash160 == null) {
			return null;
		}
		return getAccountInfo(hash160);
	}
	
	/**
	 * 通过别名查询账户hash160
	 * @param alias
	 * @return byte[]
	 */
	public byte[] getAccountHash160ByAlias(byte[] alias) {
		if(alias == null) {
			return null;
		}
		return getBytes(Sha256Hash.hash(alias));
	}
	
	/**
	 * 设置账户别名
	 * 消耗相应的信用点
	 * @param hash160
	 * @param alias
	 * @return boolean
	 */
	public boolean setAccountAlias(byte[] hash160, byte[] alias) {
		AccountStore accountInfo = getAccountInfo(hash160);
		if(accountInfo == null) {
			return false;
		}
		//设置别名
		accountInfo.setAlias(alias);
		saveAccountInfo(accountInfo);
		
		put(Sha256Hash.hash(alias), hash160);
		
		return true;
	}
	
	/**
	 * 撤销设置账户别名
	 * 消耗相应的信用点
	 * @param hash160
	 * @param alias
	 * @return boolean
	 */
	public boolean revokedSetAccountAlias(byte[] hash160, byte[] alias) {
		AccountStore accountInfo = getAccountInfo(hash160);
		if(accountInfo == null) {
			return false;
		}
		//设置别名为空
		accountInfo.setAlias(null);
		saveAccountInfo(accountInfo);
		
		put(Sha256Hash.hash(alias), hash160);
		
		return true;
	}
	
	/**
	 * 修改账户别名
	 * 消耗相应的信用点
	 * @param hash160
	 * @param alias
	 * @return boolean
	 */
	public boolean updateAccountAlias(byte[] hash160, byte[] alias) {
		AccountStore accountInfo = getAccountInfo(hash160);
		if(accountInfo == null) {
			return false;
		}
		//删除旧别名
		byte[] oldAlias = accountInfo.getAlias();
		if(oldAlias != null && oldAlias.length > 0) {
			delete(Sha256Hash.hash(accountInfo.getAlias()));
		}
		//设置新的别名
		accountInfo.setAlias(alias);
		//扣除信用
		accountInfo.setCert(accountInfo.getCert() + Configure.UPDATE_ALIAS_SUB_CREDIT);
		saveAccountInfo(accountInfo);
		
		put(Sha256Hash.hash(alias), hash160);
		
		return true;
	}
	
	/**
	 * 撤销修改账户别名
	 * 消耗相应的信用点
	 * @param hash160
	 * @param alias
	 * @return boolean
	 */
	public boolean revokedUpdateAccountAlias(byte[] hash160, byte[] alias) {
		//TODO
		//增加信用
		AccountStore accountInfo = getAccountInfo(hash160);
		if(accountInfo == null) {
			return false;
		}
		accountInfo.setCert(accountInfo.getCert() - Configure.UPDATE_ALIAS_SUB_CREDIT);
		saveAccountInfo(accountInfo);
		put(Sha256Hash.hash(alias), hash160);
		
		return true;
	}
	
	/**
	 * 添加防伪码流转信息
	 * @param antifakeCode
	 * @param hash160
	 * @param txHash
	 */
	public void addCirculation(byte[] antifakeCode, byte[] hash160, Sha256Hash txHash) {
		byte[] circulationKey = new byte[22];
		circulationKey[0] = 0;
		circulationKey[1] = 1;
		System.arraycopy(antifakeCode, 0, circulationKey, 2, Address.LENGTH);
		
		byte[] circulations = getBytes(circulationKey);
		if(circulations == null) {
			circulations = new byte[0];
		}
		byte[] newCirculations = new byte[circulations.length +  Address.LENGTH + Sha256Hash.LENGTH];
		System.arraycopy(circulations, 0, newCirculations, 0, circulations.length);
		System.arraycopy(hash160, 0, newCirculations, circulations.length, Address.LENGTH);
		System.arraycopy(txHash.getBytes(), 0, newCirculations, circulations.length + Address.LENGTH, Sha256Hash.LENGTH);
		put(circulationKey, newCirculations);
	}
	
	/**
	 * 撤销添加防伪码流转信息
	 * @param antifakeCode
	 * @param hash160
	 * @param txHash
	 */
	public void revokedAddCirculation(byte[] antifakeCode, byte[] hash160, Sha256Hash txHash) {
		byte[] circulationKey = new byte[22];
		circulationKey[0] = 0;
		circulationKey[1] = 1;
		System.arraycopy(antifakeCode, 0, circulationKey, 2, Address.LENGTH);
		
		byte[] circulations = getBytes(circulationKey);
		if(circulations == null) {
			circulations = new byte[0];
		}
		byte[] newCirculations = new byte[circulations.length - (Address.LENGTH + Sha256Hash.LENGTH)];
		
		//找出位置在哪里
		//判断在列表里面才更新，否则就被清空了
		for (int j = 0; j < circulations.length; j += (Address.LENGTH + Sha256Hash.LENGTH)) {
			byte[] addressHashsTemp = Arrays.copyOfRange(circulations, j, j + Address.LENGTH);
			byte[] txHashByte = Arrays.copyOfRange(circulations, j + Address.LENGTH, j + Address.LENGTH + Sha256Hash.LENGTH);
			if(Arrays.equals(addressHashsTemp, hash160) && Arrays.equals(txHash.getBytes(), txHashByte)) {
				System.arraycopy(circulations, 0, newCirculations, 0, j);
				int newIndex = j + Address.LENGTH + Sha256Hash.LENGTH;
				if(newIndex < circulations.length) {
					System.arraycopy(circulations, newIndex, newCirculations, j, circulations.length - newIndex);
				}
				put(circulationKey, circulations);
				return;
			}
		}
	}

	/**
	 * 获取防伪码对应人已添加的流转信息数量
	 * @param antifakeCode
	 * @param hash160
	 * @return int
	 */
	public int getCirculationCount(byte[] antifakeCode, byte[] hash160) {
		byte[] circulationKey = new byte[22];
		circulationKey[0] = 0;
		circulationKey[1] = 1;
		System.arraycopy(antifakeCode, 0, circulationKey, 2, Address.LENGTH);
		
		byte[] circulations = getBytes(circulationKey);
		
		if(circulations == null) {
			return 0;
		}
		
		int count = 0;
		for (int j = 0; j < circulations.length; j += (Address.LENGTH + Sha256Hash.LENGTH)) {
			byte[] addressHash160 = Arrays.copyOfRange(circulations, j, j + Address.LENGTH);
			if(Arrays.equals(addressHash160, hash160)) {
				count++;
			}
		}
		return count;
	}
	
	/**
	 * 获取防伪码对应已添加的流转信息数量
	 * @param antifakeCode
	 * @return int
	 */
	public int getCirculationCount(byte[] antifakeCode) {
		byte[] circulationKey = new byte[22];
		circulationKey[0] = 0;
		circulationKey[1] = 1;
		System.arraycopy(antifakeCode, 0, circulationKey, 2, Address.LENGTH);
		
		byte[] circulations = getBytes(circulationKey);
		
		if(circulations == null) {
			return 0;
		}
		
		return circulations.length / (Address.LENGTH + Sha256Hash.LENGTH);
	}
	
	/**
	 * 获取防伪码流转信息列表
	 * @param antifakeCode
	 * @return List<CirculationTransaction>
	 */
	public List<CirculationTransaction> getCirculationList(byte[] antifakeCode) {
		List<CirculationTransaction> list = new ArrayList<CirculationTransaction>();
		
		byte[] circulationKey = new byte[22];
		circulationKey[0] = 0;
		circulationKey[1] = 1;
		System.arraycopy(antifakeCode, 0, circulationKey, 2, Address.LENGTH);
		
		byte[] circulations = getBytes(circulationKey);
		
		if(circulations == null) {
			return list;
		}
		
		for (int j = 0; j < circulations.length; j += (Address.LENGTH + Sha256Hash.LENGTH)) {
			Sha256Hash txHash = Sha256Hash.wrap(Arrays.copyOfRange(circulations, j + Address.LENGTH, j + Address.LENGTH + Sha256Hash.LENGTH));
			
			TransactionStore txs = blockStoreProvider.getTransaction(txHash.getBytes());
			if(txs == null) {
				continue;
			}
			CirculationTransaction ctx = (CirculationTransaction) txs.getTransaction();
			list.add(ctx);
		}
		return list;
	}

	/**
	 * 转让防伪码
	 * @param antifakeCode
	 * @param hash160
	 * @param receiveHashs
	 * @param txHash
	 */
	public void antifakeTransfer(byte[] antifakeCode, byte[] hash160, byte[] receiveHashs, Sha256Hash txHash) {
		byte[] transferKey = new byte[22];
		transferKey[0] = 0;
		transferKey[1] = 2;
		System.arraycopy(antifakeCode, 0, transferKey, 2, Address.LENGTH);
		
		byte[] transfers = getBytes(transferKey);
		if(transfers == null) {
			transfers = new byte[0];
		}
		byte[] newTransfers = new byte[transfers.length +  Address.HASH_LENGTH + Sha256Hash.LENGTH];
		System.arraycopy(transfers, 0, newTransfers, 0, transfers.length);
		System.arraycopy(receiveHashs, 0, newTransfers, transfers.length, Address.HASH_LENGTH);
		System.arraycopy(txHash.getBytes(), 0, newTransfers, transfers.length + Address.HASH_LENGTH, Sha256Hash.LENGTH);
		
		AccountStore accountInfo = getAccountInfo(hash160);
		//扣除信用
		accountInfo.setCert(accountInfo.getCert() + Configure.TRANSFER_ANTIFAKECODE_SUB_CREDIT);
		saveAccountInfo(accountInfo);
				
		put(transferKey, newTransfers);
	}
	
	/**
	 * 撤销转让防伪码
	 * @param antifakeCode
	 * @param hash160
	 * @param receiveHashs
	 * @param txHash
	 */
	public void revokedAntifakeTransfer(byte[] antifakeCode, byte[] hash160, byte[] receiveHashs, Sha256Hash txHash) {
		byte[] transferKey = new byte[22];
		transferKey[0] = 0;
		transferKey[1] = 2;
		System.arraycopy(antifakeCode, 0, transferKey, 2, Address.LENGTH);
		
		byte[] transfers = getBytes(transferKey);
		if(transfers == null) {
			return;
		}
		byte[] newTransfers = new byte[transfers.length - (Address.HASH_LENGTH + Sha256Hash.LENGTH)];
		
		for (int j = 0; j < transfers.length; j += (Address.HASH_LENGTH + Sha256Hash.LENGTH)) {
			byte[] addressHashsTemp = Arrays.copyOfRange(transfers, j, j + Address.HASH_LENGTH);
			byte[] txHashByte = Arrays.copyOfRange(transfers, j + Address.HASH_LENGTH, j + Address.HASH_LENGTH + Sha256Hash.LENGTH);
			if(Arrays.equals(addressHashsTemp, hash160) && Arrays.equals(txHash.getBytes(), txHashByte)) {
				System.arraycopy(transfers, 0, newTransfers, 0, j);
				
				int newIndex = j + Address.HASH_LENGTH + Sha256Hash.LENGTH;
				if(newIndex < transfers.length) {
					System.arraycopy(transfers, j + newIndex, newTransfers, j, transfers.length - newIndex);
				}
				put(transferKey, newTransfers);
				
				AccountStore accountInfo = getAccountInfo(hash160);
				//扣除信用
				accountInfo.setCert(accountInfo.getCert() - Configure.TRANSFER_ANTIFAKECODE_SUB_CREDIT);
				saveAccountInfo(accountInfo);
				return;
			}
		}
	}
	
	/**
	 * 获取防伪码的拥有者，流转链的最后一个人，如果流转链没有，则是验证者，会返回null，调用者自行判断
	 * @param antifakeCode
	 * @return byte[]
	 */
	public byte[] getAntifakeCodeOwner(byte[] antifakeCode) {
		byte[] transferKey = new byte[22];
		transferKey[0] = 0;
		transferKey[1] = 2;
		System.arraycopy(antifakeCode, 0, transferKey, 2, Address.LENGTH);
		
		byte[] transfers = getBytes(transferKey);
		if(transfers == null || transfers.length == 0) {
			return null;
		}
		int count = transfers.length / (Address.HASH_LENGTH + Sha256Hash.LENGTH);
		int index = (count - 1) * (Address.HASH_LENGTH + Sha256Hash.LENGTH);
		return Arrays.copyOfRange(transfers, index, index + Address.HASH_LENGTH);
	}
	
	/**
	 * 获取防伪码的转让次数
	 * @param antifakeCode
	 * @return int
	 */
	public int getAntifakeCodeTransferCount(byte[] antifakeCode) {
		byte[] transferKey = new byte[22];
		transferKey[0] = 0;
		transferKey[1] = 2;
		System.arraycopy(antifakeCode, 0, transferKey, 2, Address.LENGTH);
		
		byte[] transfers = getBytes(transferKey);
		if(transfers == null || transfers.length == 0) {
			return 0;
		}
		return transfers.length / (Address.HASH_LENGTH + Sha256Hash.LENGTH);
	}
	
	/**
	 * 获取防伪码转让列表
	 * @param antifakeCode
	 * @return List<CirculationTransaction>
	 */
	public List<AntifakeTransferTransaction> getAntifakeCodeTransferList(byte[] antifakeCode) {
		List<AntifakeTransferTransaction> list = new ArrayList<AntifakeTransferTransaction>();
		
		byte[] transferKey = new byte[22];
		transferKey[0] = 0;
		transferKey[1] = 2;
		System.arraycopy(antifakeCode, 0, transferKey, 2, Address.LENGTH);
		
		byte[] transfers = getBytes(transferKey);
		
		if(transfers == null) {
			return list;
		}
		
		for (int j = 0; j < transfers.length; j += (Address.HASH_LENGTH + Sha256Hash.LENGTH)) {
			Sha256Hash txHash = Sha256Hash.wrap(Arrays.copyOfRange(transfers, j + Address.HASH_LENGTH, j + Address.HASH_LENGTH + Sha256Hash.LENGTH));
			
			TransactionStore txs = blockStoreProvider.getTransaction(txHash.getBytes());
			if(txs == null) {
				continue;
			}
			AntifakeTransferTransaction atx = (AntifakeTransferTransaction) txs.getTransaction();
			list.add(atx);
		}
		return list;
	}

	/**
	 * 获取防伪码验证的交易
	 * @param antifakeCode
	 * @return Sha256Hash
	 */
	public Sha256Hash getAntifakeVerifyTx(byte[] antifakeCode) {
		byte[] key = new byte[22];
		key[0] = 0;
		key[1] = 3;
		System.arraycopy(antifakeCode, 0, key, 2, Address.LENGTH);
		
		byte[] content = getBytes(key);
		if(content == null) {
			return null;
		}
		return Sha256Hash.wrap(content);
	}

	/**
	 * 防伪码验证
	 * @param antifakeCode
	 * @param hash
	 */
	public void verifyAntifakeCode(byte[] antifakeCode, Sha256Hash hash) {
		
		byte[] key = new byte[22];
		key[0] = 0;
		key[1] = 3;
		System.arraycopy(antifakeCode, 0, key, 2, Address.LENGTH);
		
		put(key, hash.getBytes());
	}
	
	/**
	 * 撤销防伪码验证
	 * @param antifakeCode
	 */
	public void revokedVerifyAntifakeCode(byte[] antifakeCode) {
		
		byte[] key = new byte[22];
		key[0] = 0;
		key[1] = 3;
		System.arraycopy(antifakeCode, 0, key, 2, Address.LENGTH);
		
		delete(key);
	}
	
	/**
	 * 共识节点加入
	 * @param tx
	 */
	public void addConsensus(RegConsensusTransaction tx) {
		consensusLocker.lock();
		try {
			//注册共识，加入到共识账户列表中
			byte[] consensusAccountHash160s = getBytes(Configure.CONSENSUS_ACCOUNT_KEYS);
			if(consensusAccountHash160s == null) {
				consensusAccountHash160s = new byte[0];
			}
			byte[] hash160 = tx.getHash160();
			byte[] packager = tx.getPackager();
			byte[] newConsensusHash160s = new byte[consensusAccountHash160s.length + (2 * Address.LENGTH + Sha256Hash.LENGTH)];
			System.arraycopy(consensusAccountHash160s, 0, newConsensusHash160s, 0, consensusAccountHash160s.length);
			System.arraycopy(hash160, 0, newConsensusHash160s, consensusAccountHash160s.length, Address.LENGTH);
			System.arraycopy(packager, 0, newConsensusHash160s, consensusAccountHash160s.length + Address.LENGTH, Address.LENGTH);
			System.arraycopy(tx.getHash().getBytes(), 0, newConsensusHash160s, consensusAccountHash160s.length + 2 * Address.LENGTH, Sha256Hash.LENGTH);
			put(Configure.CONSENSUS_ACCOUNT_KEYS, newConsensusHash160s);

			//添加账户信息，如果不存在的话
			AccountStore accountInfo = getAccountInfo(hash160);
			if(accountInfo == null) {
				//理论上只有普通账户才有可能没信息，注册账户没有注册信息的话，交易验证不通过
				accountInfo = createNewAccountInfo(tx, AccountBody.empty(), new byte[][] {tx.getPubkey()});
				accountInfo.setSupervisor(null);
				accountInfo.setLevel(0);
				put(hash160, accountInfo.baseSerialize());
			} else {
				//不确定的账户，现在可以确定下来了
				updateAccountInfo(accountInfo, tx);
			}
			//添加到共识缓存器里
			consensusPool.add(new ConsensusModel(tx.getHash(), tx.getHash160(), tx.getPackager()));
		} catch (Exception e) {
			log.error("出错了{}", e.getMessage(), e);
		} finally {
			consensusLocker.unlock();
		}
	}
	
	/**
	 * 退出共识
	 * @param tx
	 */
	public void removeConsensus(Transaction tx) {
		byte[] hash160 = null;
		if(tx instanceof RemConsensusTransaction) {
			//主动退出共识
			RemConsensusTransaction remTransaction = (RemConsensusTransaction)tx;
			hash160 = remTransaction.getHash160();
		} else {
			//违规被提出共识
			ViolationTransaction vtx = (ViolationTransaction)tx;
			hash160 = vtx.getViolationEvidence().getAudienceHash160();
		}

		//从集合中删除共识节点
		deleteConsensusFromCollection(hash160);

		//退出的账户
		if(tx instanceof ViolationTransaction) {

			//违规被提出共识，增加规则证据到状态里，以便查证
			ViolationTransaction vtx = (ViolationTransaction)tx;
			ViolationEvidence violationEvidence = vtx.getViolationEvidence();
			Sha256Hash evidenceHash = violationEvidence.getEvidenceHash();
			put(evidenceHash.getBytes(), tx.getHash().getBytes());

			//如果是惩罚，则对委托人进行处理
			TransactionStore regTxStore = blockStoreProvider.getTransaction(tx.getInput(0).getFroms().get(0).getParent().getHash().getBytes());
			RegConsensusTransaction regtx = (RegConsensusTransaction) regTxStore.getTransaction();
			hash160 = regtx.getHash160();

			//减去相应的信用值
			AccountStore accountInfo = getAccountInfo(hash160);
			long certChange = 0;
			if(violationEvidence.getViolationType() == ViolationEvidence.VIOLATION_TYPE_NOT_BROADCAST_BLOCK) {
				certChange = Configure.CERT_CHANGE_TIME_OUT;
			} else if(violationEvidence.getViolationType() == ViolationEvidence.VIOLATION_TYPE_REPEAT_BROADCAST_BLOCK) {
				certChange = Configure.CERT_CHANGE_SERIOUS_VIOLATION;
			}
			
			accountInfo.setCert(accountInfo.getCert() + certChange);
			
			saveAccountInfo(accountInfo);
		}
	}

	/**
	 * 从集合中删除共识节点
	 * @param hash160
	 */
	public void deleteConsensusFromCollection(byte[] hash160) {
		consensusLocker.lock();
		try {
			//从共识账户列表中删除
			byte[] consensusAccountHash160s = getBytes(Configure.CONSENSUS_ACCOUNT_KEYS);
			
			byte[] newConsensusHash160s = new byte[consensusAccountHash160s.length - (2 * Address.LENGTH + Sha256Hash.LENGTH)];
			
			//找出位置在哪里
			//判断在列表里面才更新，否则就被清空了
			for (int j = 0; j < consensusAccountHash160s.length; j += (2 * Address.LENGTH + Sha256Hash.LENGTH)) {
				byte[] addressHash160 = Arrays.copyOfRange(consensusAccountHash160s, j, j + Address.LENGTH);
				byte[] packagerHash160 = Arrays.copyOfRange(consensusAccountHash160s, j + Address.LENGTH, j + 2 * Address.LENGTH);
				if(Arrays.equals(addressHash160, hash160) || Arrays.equals(packagerHash160, hash160)) {
					System.arraycopy(consensusAccountHash160s, 0, newConsensusHash160s, 0, j);
					
					int newIndex = j + 2 * Address.LENGTH + Sha256Hash.LENGTH;
					if(newIndex < consensusAccountHash160s.length) {
						System.arraycopy(consensusAccountHash160s, newIndex, newConsensusHash160s, j, consensusAccountHash160s.length - newIndex);
					}
					
					put(Configure.CONSENSUS_ACCOUNT_KEYS, newConsensusHash160s);
					break;
				}
			}
			//从共识缓存器里中移除
			consensusPool.delete(hash160);
		} catch (Exception e) {
			log.error("出错了{}", e.getMessage(), e);
		} finally {
			consensusLocker.unlock();
		}
	}

	public void addRevokeCertAccount(CertAccountRevokeTransaction tx){
		if(isCertAccountRevoked(tx.getRevokeHash160()))
			return;
		revokeLock.lock();
		try {
			byte[] revokedAccountHash160s = getBytes(Configure.REVOKED_CERT_ACCOUNT_KEYS);
			if(revokedAccountHash160s == null) {
				revokedAccountHash160s = new byte[0];
			}
			byte[] hash160 = tx.getRevokeHash160();
			byte[] byhash160 = tx.getHash160();
			byte[] newrevokedAccountHash160s = new byte[revokedAccountHash160s.length + (Address.LENGTH * 2)];
			System.arraycopy(revokedAccountHash160s, 0, newrevokedAccountHash160s, 0, revokedAccountHash160s.length);
			System.arraycopy(hash160, 0, newrevokedAccountHash160s, revokedAccountHash160s.length, Address.LENGTH);
			System.arraycopy(byhash160, 0, newrevokedAccountHash160s, revokedAccountHash160s.length+Address.LENGTH, Address.LENGTH);
			put(Configure.REVOKED_CERT_ACCOUNT_KEYS,newrevokedAccountHash160s);
		}catch (Exception e){
			log.error("出错了{}", e.getMessage(), e);
		}finally {
			revokeLock.unlock();
		}
	}

	public void deleteRevokeCertAccount(CertAccountRevokeTransaction tx){
		if(!isCertAccountRevoked(tx.getRevokeHash160()))
			return;
		revokeLock.lock();
		try {
			byte[] revokedAccountHash160s = getBytes(Configure.REVOKED_CERT_ACCOUNT_KEYS);

			byte[] hash160 = tx.getRevokeHash160();
			byte[] byhash160 = tx.getHash160();
			byte[] newrevokedAccountHash160s = new byte[revokedAccountHash160s.length - (Address.LENGTH * 2)];
			byte[] tmpbyte = new byte[Address.LENGTH];
			for(int j=0;j<revokedAccountHash160s.length;j+= Address.LENGTH * 2) {
				System.arraycopy(revokedAccountHash160s,j,tmpbyte,0,Address.LENGTH);
				if(hash160.equals(tmpbyte)){
					System.arraycopy(revokedAccountHash160s,0,newrevokedAccountHash160s,0,j);
					System.arraycopy(revokedAccountHash160s,j+2*Address.LENGTH , newrevokedAccountHash160s,j,revokedAccountHash160s.length-j-2*Address.LENGTH);
					break;
				}
			}
			put(Configure.REVOKED_CERT_ACCOUNT_KEYS,newrevokedAccountHash160s);
		}catch (Exception e){
			log.error("出错了{}", e.getMessage(), e);
		}finally {
			revokeLock.unlock();
		}
	}

	public boolean isCertAccountRevoked(byte[] hash160){
		byte[] revokedAccountHash160s = getBytes(Configure.REVOKED_CERT_ACCOUNT_KEYS);
		if(revokedAccountHash160s == null)
			return false;

		for (int j = 0; j < revokedAccountHash160s.length; j += (Address.LENGTH *2)) {
			byte[] addressHash160 = Arrays.copyOfRange(revokedAccountHash160s, j, j + Address.LENGTH);
			if(Arrays.equals(hash160,addressHash160))
				return true;
		}
		return false;
	}


	
	/**
	 * 不确定的账户，确定下来
	 * @param accountInfo
	 * @param tx
	 */
	public void updateAccountInfo(AccountStore accountInfo, BaseCommonlyTransaction tx) {
		if(accountInfo != null && accountInfo.getType() == 0) {
			accountInfo.setType(tx.isSystemAccount() ? network.getSystemAccountVersion() : network.getCertAccountVersion());
			
			if(tx.isCertAccount()) {
				accountInfo.setInfoTxid(tx.getHash());
				CertAccountRegisterTransaction rtx = (CertAccountRegisterTransaction) tx;
				byte[][] pubkeys = new byte[][] {rtx.getMgPubkeys()[0], rtx.getMgPubkeys()[1], rtx.getTrPubkeys()[0], rtx.getTrPubkeys()[1]};
				accountInfo.setPubkeys(pubkeys);
			} else {
				accountInfo.setPubkeys(new byte[][] { tx.getPubkey() });
			}
			saveAccountInfo(accountInfo);
		}
	}
	
	/**
	 * 创建一个新的账户存储信息
	 * @param tx
	 * @param accountBody
	 * @param pubkeys
	 * @return AccountStore
	 */
	public AccountStore createNewAccountInfo(BaseCommonlyTransaction tx, AccountBody accountBody, byte[][] pubkeys) {
		AccountStore accountInfo = new AccountStore(network);
		accountInfo.setHash160(tx.getHash160());
		accountInfo.setType(tx.isSystemAccount() ? network.getSystemAccountVersion() : network.getCertAccountVersion());
		accountInfo.setStatus((byte)0);
		accountInfo.setCert(0);
		accountInfo.setAccountBody(accountBody);
		accountInfo.setBalance(Coin.ZERO.value);
		accountInfo.setCreateTime(tx.getTime());
		accountInfo.setLastModifyTime(tx.getTime());
		accountInfo.setInfoTxid(tx.getHash());
		accountInfo.setPubkeys(pubkeys);
		return accountInfo;
	}

	/**
	 * 回滚过程中的共识重新加入
	 * @param tx
	 */
	public void revokedConsensus(Transaction tx) {
		
		byte[] hash160 = null;
		if(tx instanceof RemConsensusTransaction) {
			//主动退出共识
			RemConsensusTransaction remTransaction = (RemConsensusTransaction)tx;
			hash160 = remTransaction.getHash160();
		} else {
			//违规被提出共识
			ViolationTransaction vtx = (ViolationTransaction)tx;
			hash160 = vtx.getViolationEvidence().getAudienceHash160();
		}
		
		//重新加入共识账户列表中
		//注册共识的交易
		Sha256Hash txhash = tx.getInput(0).getFroms().get(0).getParent().getHash();
		
		TransactionStore regTxStore = blockStoreProvider.getTransaction(txhash.getBytes());
		if(regTxStore == null) {
			return;
		}
		RegConsensusTransaction regTx = (RegConsensusTransaction) regTxStore.getTransaction();
		
		addConsensus(regTx);
		
		//退出的账户
		if(tx instanceof ViolationTransaction) {
			//委托人
			hash160 = regTx.getHash160();

			//违规被提出共识，删除证据
			ViolationTransaction vtx = (ViolationTransaction)tx;
			ViolationEvidence violationEvidence = vtx.getViolationEvidence();
			Sha256Hash evidenceHash = violationEvidence.getEvidenceHash();
			delete(evidenceHash.getBytes());
			
			//加上之前减去的信用值
			AccountStore accountInfo = getAccountInfo(hash160);
			long certChange = 0;
			if(violationEvidence.getViolationType() == ViolationEvidence.VIOLATION_TYPE_NOT_BROADCAST_BLOCK) {
				certChange = Configure.CERT_CHANGE_TIME_OUT;
			} else if(violationEvidence.getViolationType() == ViolationEvidence.VIOLATION_TYPE_REPEAT_BROADCAST_BLOCK) {
				certChange = Configure.CERT_CHANGE_SERIOUS_VIOLATION;
			}
			accountInfo.setCert(accountInfo.getCert() - certChange);
			saveAccountInfo(accountInfo);
		}
	}

	/**
	 * 资产登记
	 * @param assetsRegisterTx
	 */
	public void assetsRegister(AssetsRegisterTransaction assetsRegisterTx) {

		//注册资产，加入到资产注册列表中
		byte[] assetsRegHash256s = getBytes(Configure.ASSETS_REG_LIST_KEYS);
		if(assetsRegHash256s == null) {
			assetsRegHash256s = new byte[0];
		}

		byte[] newHash256s = new byte[assetsRegHash256s.length + Sha256Hash.LENGTH];
		byte[] txHash = assetsRegisterTx.getHash().getBytes();
		System.arraycopy(assetsRegHash256s, 0, newHash256s, 0, assetsRegHash256s.length);
		System.arraycopy(txHash, 0, newHash256s, assetsRegHash256s.length, Sha256Hash.LENGTH);
		put(Configure.ASSETS_REG_LIST_KEYS, newHash256s);


		//再单独用交易的code作为建存储交易，方便判断是否有重复资产登记的交易
		byte[] registerKey = Sha256Hash.hash(assetsRegisterTx.getCode());
		put(registerKey, txHash);
	}

	/**
	 * 判断资产是否已注册
	 * @param code
	 * @return
	 */
	public boolean hasAssetsReg(byte[] code) {
		byte[] codeKey = Sha256Hash.hash(code);
		byte[] result = getBytes(codeKey);
		if(result != null) {
			return true;
		}
		return false;
	}

	/**
	 * 根据code获取注册资产
	 * @param code
	 * @return
	 */
	public AssetsRegisterTransaction getAssetsRegisterTxByCode(byte[] code) {
		return getAssetsRegisterTxByCodeHash256(Sha256Hash.hash(code));
	}

	/**
	 * 资产注册交易回滚
	 * @param tx
	 */
	public void rollbackAssetsRegisterTx(Transaction tx) {
		AssetsRegisterTransaction assetsRegisterTx = (AssetsRegisterTransaction)tx;
		//首先根据code ，删除注册交易
		byte[] registerKey = Sha256Hash.hash(assetsRegisterTx.getCode());
		byte[] txHash = tx.getHash().getBytes();
		delete(registerKey);

		//再从资产注册列表中，删除交易
		byte[] assetsRegHash256s = getBytes(Configure.ASSETS_REG_LIST_KEYS);
		if(assetsRegHash256s == null) {
			return;
		}
		//如果刚好存储的值相等，直接删除
		if(assetsRegHash256s.length == Sha256Hash.LENGTH) {
			if(Arrays.equals(txHash, assetsRegHash256s)) {
				delete(Configure.ASSETS_REG_LIST_KEYS);
			}
			return;
		}

		for(int j = 0; j < assetsRegHash256s.length; j++) {
			byte[] current = new byte[Sha256Hash.LENGTH];
			System.arraycopy(assetsRegHash256s, j, current, 0, Sha256Hash.LENGTH);
			if(Arrays.equals(current, txHash)) {
				byte [] before = new byte[j];
				System.arraycopy(assetsRegHash256s, 0, before, 0, j);

				byte [] end = new byte[assetsRegHash256s.length - j - Sha256Hash.LENGTH];
				System.arraycopy(assetsRegHash256s, j + Sha256Hash.LENGTH, end, 0, end.length);

				//newHash = before + end;
				byte []newHash = new byte[assetsRegHash256s.length - Sha256Hash.LENGTH];
				System.arraycopy(before, 0, newHash, 0, before.length);
				System.arraycopy(end, 0, newHash, before.length, end.length);

				put(Configure.ASSETS_REG_LIST_KEYS, newHash);
				break;
			}
		}
	}

	/**
	 * 根据hash256(code)获取注册资产
	 * @param code
	 * @return
	 */
	public AssetsRegisterTransaction getAssetsRegisterTxByCodeHash256(byte[] code) {
		byte[] result = getBytes(code);
		if(result == null) {
			return null;
		}
		Sha256Hash txHash = Sha256Hash.wrap(Arrays.copyOfRange(result,0, Sha256Hash.LENGTH));
		TransactionStore txs = blockStoreProvider.getTransaction(txHash.getBytes());
		if(txs == null) {
			return null;
		}
		return (AssetsRegisterTransaction) txs.getTransaction();
	}

	/**
	 * 获取注册资产列表
	 * @return
	 */
	public List<TransactionStore> getAssetRegList() {
		List<TransactionStore> list = new ArrayList<>();

		byte[] assetsRegHash256s = getBytes(Configure.ASSETS_REG_LIST_KEYS);
		if(assetsRegHash256s == null) {
			return list;
		}

		for (int j = 0; j < assetsRegHash256s.length; j += Sha256Hash.LENGTH) {
			Sha256Hash txHash = Sha256Hash.wrap(Arrays.copyOfRange(assetsRegHash256s, j, j + Sha256Hash.LENGTH));
			TransactionStore txs = blockStoreProvider.getTransaction(txHash.getBytes());
			list.add(txs);
		}
		return list;
	}


	/**
	 * 资产发行
	 * 资产发行后，用所注册的资产的code + 前缀[1],[1]作为key，存储在一个列表里
	 * @param assetsIssuedTx
	 */
	public void assetsIssued(AssetsIssuedTransaction assetsIssuedTx) {
		assetsLock.lock();
		try {
			//1. 首先找到注册交易，然后通过注册交易的code，生成存储资产发行列表的key
			TransactionStore txs =  blockStoreProvider.getTransaction(assetsIssuedTx.getAssetsHash().getBytes());
			AssetsRegisterTransaction assetsRegisterTx = (AssetsRegisterTransaction)txs.getTransaction();

			//资产发行列表的key = [1],[1] + hash256(registerTx.code)
			byte[] key = new byte[Sha256Hash.LENGTH + 2];
			byte[] hash256 = Sha256Hash.hash(assetsRegisterTx.getCode());
			System.arraycopy(Configure.ASSETS_ISSUE_FIRST_KEYS, 0, key, 0, Configure.ASSETS_ISSUE_FIRST_KEYS.length);
			System.arraycopy(hash256, 0, key, 2, hash256.length);

			//获取已存储的资产发行列表
			byte[] assetsIssueHash256s = getBytes(key);
			if(assetsIssueHash256s == null) {
				assetsIssueHash256s = new byte[0];
			}

			byte[] newHash256s = new byte[assetsIssueHash256s.length + Sha256Hash.LENGTH];
			byte[] txHash = assetsIssuedTx.getHash().getBytes();
			//将新交易存入进列表
			System.arraycopy(assetsIssueHash256s, 0, newHash256s, 0, assetsIssueHash256s.length);
			System.arraycopy(txHash, 0, newHash256s, assetsIssueHash256s.length, txHash.length);
			put(key, newHash256s);

			//资产发行列表存储后，需要维护接收人的资产账户信息
			updateAccountAssets(hash256, assetsIssuedTx.getReceiver(), assetsIssuedTx.getAmount(), 1);
		}catch (Exception e) {
			log.error("出错了{}", e.getMessage(), e);
		}finally {
			assetsLock.unlock();
		}
	}

	/**
	 * 资产发行交易回滚
	 * @param tx
	 */
	public void rollbackAssetsIssueTx(Transaction tx) {
		AssetsIssuedTransaction assetsIssuedTx = (AssetsIssuedTransaction)tx;
		assetsLock.lock();
		try {
			//1. 首先找到注册交易，然后通过注册交易的code，生成存储资产发行列表的key
			TransactionStore txs =  blockStoreProvider.getTransaction(assetsIssuedTx.getAssetsHash().getBytes());
			AssetsRegisterTransaction assetsRegisterTx = (AssetsRegisterTransaction)txs.getTransaction();

			//资产发行列表的key = [1],[1] + hash256(registerTx.code)
			byte[] key = new byte[Sha256Hash.LENGTH + 2];
			byte[] hash256 = Sha256Hash.hash(assetsRegisterTx.getCode());
			System.arraycopy(Configure.ASSETS_ISSUE_FIRST_KEYS, 0, key, 0, Configure.ASSETS_ISSUE_FIRST_KEYS.length);
			System.arraycopy(hash256, 0, key, 2, hash256.length);

			//获取已存储的资产发行列表
			byte[] txHash = assetsIssuedTx.getHash().getBytes();
			byte[] assetsIssueHash256s = getBytes(key);
			if(assetsIssueHash256s == null) {
				return;
			}
			if(assetsIssueHash256s.length == Sha256Hash.LENGTH) {
				if(Arrays.equals(txHash, assetsIssueHash256s)) {
					delete(key);
				}
			}else {
				//找到该笔资产发行交易，然后删除
				for(int j = 0; j < assetsIssueHash256s.length; j++) {
					byte[] current = new byte[Sha256Hash.LENGTH];
					System.arraycopy(assetsIssueHash256s, j, current, 0, Sha256Hash.LENGTH);
					if(Arrays.equals(current, txHash)) {
						byte [] before = new byte[j];
						System.arraycopy(assetsIssueHash256s, 0, before, 0, j);

						byte [] end = new byte[assetsIssueHash256s.length - j - Sha256Hash.LENGTH];
						System.arraycopy(assetsIssueHash256s, j + Sha256Hash.LENGTH, end, 0, end.length);

						//newHash = before + end;
						byte []newHash = new byte[assetsIssueHash256s.length - Sha256Hash.LENGTH];
						System.arraycopy(before, 0, newHash, 0, before.length);
						System.arraycopy(end, 0, newHash, before.length, end.length);

						put(key, newHash);
						break;
					}
				}
			}

			//从我的资产账户中，清除这笔交易
			updateAccountAssets(hash256, assetsIssuedTx.getReceiver(), assetsIssuedTx.getAmount(), -1);
		}catch (Exception e) {
			log.error("出错了{}", e.getMessage(), e);
		}finally {
			assetsLock.unlock();
		}
	}

	/**
	 * 更新账户的资产信息
	 * @param code 注册交易的code
	 * @param addressHash 用户地址的hash
	 * @param amount 变动的金额
	 * @param symbol 变动的方向  1，-1
	 */
	private void updateAccountAssets(byte[] code,  byte[] addressHash, long amount, int symbol) {
		//资产账户的key 规则为 [1],[1] + recevier
		byte[] key = new byte[addressHash.length + 2];
		//固定key的前两位为 [1],[1]
		System.arraycopy(Configure.ASSETS_ISSUE_FIRST_KEYS, 0, key, 0, Configure.ASSETS_ISSUE_FIRST_KEYS.length);
		System.arraycopy(addressHash, 0, key, 2, addressHash.length);

		//获取接收人资产账户列表
		byte[] myAssets = getBytes(key);

		if(myAssets == null && symbol == 1) {
			//如果资产账户列表为空，直接新增
			Assets assets = new Assets(code, amount);
			myAssets = assets.serialize();
			put(key, myAssets);
		}else {
			// 查询是否已存在于资产列表中
			boolean hasAssets = false;
			for(int j = 0; j < myAssets.length; j += Assets.CODE_LENGTH + 8) {
				byte[] current = new byte[Assets.CODE_LENGTH + 8];
				System.arraycopy(myAssets, j, current, 0, Assets.CODE_LENGTH + 8);
				Assets assets = new Assets(current);

				//如果存在，则在以前的资产上添加，然后重新保存到列表中
				if(Arrays.equals(assets.getCode(), code)) {
					assets.setBalance(assets.getBalance() + symbol * amount);

					byte [] before = new byte[j];
					System.arraycopy(myAssets, 0, before, 0, j);

					byte [] end = new byte[myAssets.length - j - Assets.CODE_LENGTH - 8 ];
					System.arraycopy(myAssets, j + Assets.CODE_LENGTH + 8, end, 0, end.length);

					//newAssets = before + assets.serialize() + end;
					byte [] newAssets = new byte[myAssets.length];
					System.arraycopy(before, 0, newAssets, 0, before.length);
					System.arraycopy(assets.serialize(), 0, newAssets, before.length, Assets.CODE_LENGTH + 8);
					System.arraycopy(end, 0, newAssets, before.length + Assets.CODE_LENGTH + 8, end.length);

					put(key, newAssets);
					hasAssets = true;
					break;
				}
			}
			//如果没有则在后面新增
			if(!hasAssets && symbol == 1) {
				byte [] newAssets = new byte[myAssets.length + Assets.CODE_LENGTH + 8];
				System.arraycopy(myAssets, 0, newAssets, 0, myAssets.length);

				Assets assets = new Assets(code, amount);
				System.arraycopy(assets.serialize(), 0, newAssets, myAssets.length, Assets.CODE_LENGTH + 8);
				put(key, newAssets);
			}
		}
	}

	/**
	 * 获取资产发行列表
	 * @param code
	 * @return
	 */
	public List<TransactionStore> getAssetsIssueList(byte[] code) {
		//资产发行列表的key = [1],[1] + hash256(registerTx.code)
		byte[] key = new byte[Sha256Hash.LENGTH + 2];
		byte[] hash256 = Sha256Hash.hash(code);
		//固定key的前两位为 1,1
		System.arraycopy(Configure.ASSETS_ISSUE_FIRST_KEYS, 0, key, 0, Configure.ASSETS_ISSUE_FIRST_KEYS.length);
		System.arraycopy(hash256, 0, key, 2, hash256.length);

		List<TransactionStore> list = new ArrayList<>();
		byte[] assetsRegHash256s = getBytes(key);
		if(assetsRegHash256s == null) {
			return list;
		}

		for (int j = 0; j < assetsRegHash256s.length; j += Sha256Hash.LENGTH) {
			Sha256Hash txHash = Sha256Hash.wrap(Arrays.copyOfRange(assetsRegHash256s, j, j + Sha256Hash.LENGTH));
			TransactionStore txs = blockStoreProvider.getTransaction(txHash.getBytes());
			list.add(txs);
		}
		return list;
	}

	/**
	 * 获取资产发行总金额
	 * @param code
	 * @return
	 */
	public Long getAssetsIssueAmount(byte[] code) {
		//资产发行列表的key = [1],[1] + hash256(registerTx.code)
		byte[] key = new byte[Sha256Hash.LENGTH + 2];
		byte[] hash256 = Sha256Hash.hash(code);
		//固定key的前两位为 1,1
		System.arraycopy(Configure.ASSETS_ISSUE_FIRST_KEYS, 0, key, 0, Configure.ASSETS_ISSUE_FIRST_KEYS.length);
		System.arraycopy(hash256, 0, key, 2, hash256.length);
		byte[] assetsRegHash256s = getBytes(key);
		if(assetsRegHash256s == null) {
			return 0L;
		}
		Long amount = 0L;
		for (int j = 0; j < assetsRegHash256s.length; j += Sha256Hash.LENGTH) {
			Sha256Hash txHash = Sha256Hash.wrap(Arrays.copyOfRange(assetsRegHash256s, j, j + Sha256Hash.LENGTH));
			TransactionStore txs = blockStoreProvider.getTransaction(txHash.getBytes());
			amount += ((AssetsIssuedTransaction)txs.getTransaction()).getAmount();
		}
		return amount;
	}

	/**
	 *  获取我的资产
	 * @param addressHash
	 * @return
	 */
	public List<Assets> getMyAssetsAccount(byte[] addressHash) {
		//资产账户的key 规则为 [1],[1] + recevier
		byte[] key = new byte[addressHash.length + 2];
		System.arraycopy(Configure.ASSETS_ISSUE_FIRST_KEYS, 0, key, 0, Configure.ASSETS_ISSUE_FIRST_KEYS.length);
		System.arraycopy(addressHash, 0, key, 2, addressHash.length);

		byte[] myAssets = getBytes(key);
		if(myAssets == null) {
			myAssets = new byte[0];
		}
		List<Assets> assetsList = new ArrayList<>();

		for(int j = 0; j < myAssets.length; j += Assets.CODE_LENGTH + 8) {
			byte[] current = new byte[Assets.CODE_LENGTH + 8];
			System.arraycopy(myAssets, j, current, 0, Assets.CODE_LENGTH + 8);
			Assets assets = new Assets(current);
			assetsList.add(assets);
		}
		return assetsList;
	}

	/**
	 *  根据资产code，获取我相关的资产信息
	 * @param addressHash
	 * @param code
	 * @return
	 */
	public Assets getMyAssetsByCode(byte[] addressHash, byte[] code) {
		List<Assets> assetsList = getMyAssetsAccount(addressHash);
		if(assetsList == null || assetsList.size() == 0) {
			return null;
		}

		for(Assets assets : assetsList) {
			if(Arrays.equals(assets.getCode(), code)) {
				return assets;
			}
		}
		return null;
	}

	/**
	 * 资产转让
	 * @param assetsTransferTx
	 */
	public void assetsTransfer(AssetsTransferTransaction assetsTransferTx) {
		assetsLock.lock();
		try{
			TransactionStore txs =  blockStoreProvider.getTransaction(assetsTransferTx.getAssetsHash().getBytes());
			AssetsRegisterTransaction assetsRegisterTx = (AssetsRegisterTransaction)txs.getTransaction();

			byte[] sender = assetsTransferTx.getHash160();
			byte[] receiver = assetsTransferTx.getReceiver();
			//首先判断转让人 余额是否充足
			Assets assets = getMyAssetsByCode(sender, Sha256Hash.hash(assetsRegisterTx.getCode()));
			if(assets == null) {
				throw new RuntimeException("转让人没有与资产相关的信息");
			}
			if(assets.getBalance() < assetsTransferTx.getAmount()) {
				throw new RuntimeException("转让人资产余额不足");
			}

			byte[] hash256 = Sha256Hash.hash(assetsRegisterTx.getCode());
			updateAccountAssets(hash256, sender, assetsTransferTx.getAmount(), -1);
			updateAccountAssets(hash256, receiver, assetsTransferTx.getAmount(), 1);
		}catch (Exception e) {
			log.error("出错了{}", e.getMessage(), e);
		}finally {
			assetsLock.unlock();
		}
	}

	/**
	 * 资产转让交易回滚
	 * @param tx
	 */
	public void rollbackAssetsTransferTx(Transaction tx) {
		assetsLock.lock();
		try {
			AssetsTransferTransaction transferTx = (AssetsTransferTransaction)tx;

			byte[] sender =  transferTx.getHash160();
			AccountStore accountStore = getAccountInfo(sender);
			if(accountStore == null) {
				throw new RuntimeException("转账账户未找到");
			}
			Address address = new Address(network,accountStore.getType(), sender);
			byte[] receiver = transferTx.getReceiver();
			//与资产转让交易做反向操作
			TransactionStore txs =  blockStoreProvider.getTransaction(transferTx.getAssetsHash().getBytes());
			AssetsRegisterTransaction assetsRegisterTx = (AssetsRegisterTransaction)txs.getTransaction();
			byte[] hash256 = Sha256Hash.hash(assetsRegisterTx.getCode());
			updateAccountAssets(hash256, address.getHash(), transferTx.getAmount(),  1);
			updateAccountAssets(hash256, receiver, transferTx.getAmount(), -1);
		}catch (Exception e) {
			log.error("出错了{}", e.getMessage(), e);
		}finally {
			assetsLock.unlock();
		}
	}

	public void clean() {
		//清除老数据
		DBIterator iterator = db.getSourceDb().iterator();
		while(iterator.hasNext()) {
			Entry<byte[], byte[]> item = iterator.next();
			byte[] key = item.getKey();
			delete(key);
		}
	}
}
