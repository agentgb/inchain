package org.inchain.rpc;

import java.io.IOException;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.inchain.account.AccountBody;
import org.inchain.account.Address;
import org.inchain.core.Coin;
import org.inchain.core.Product;
import org.inchain.core.exception.VerificationException;

/**
 * RPCService
/**
 * 
 * 核心客户端RPC服务，RP
 * 命令列表：
 * help    帮助命令，列表出所有命令
 * 
 * --- 区块相关
 * getblockcount 				获取区块的数量
 * getnewestblockheight 		获取最新区块的高度 
 * getnewestblockhash			获取最新区块的hash
 * getblockheader [param] (block hash or height)	通过区块的hash或者高度获取区块的头信息
 * getblock		  [param] (block hash or height)	通过区块的hash或者高度获取区块的完整信息
 * 
 * --- 内存池
 * getmempoolinfo [count] 		获取内存里的count条交易
 * 
 * --- 帐户
 * newaccount [mgpw trpw]		创建帐户，同时必需指定帐户管理密码和交易密码
 * getaccountaddress			获取帐户的地址
 * getaccountpubkeys			获取帐户的公钥
 * dumpprivateseed 				备份私钥种子，同时显示帐户的hash160
 * 
 * getbalance					获取帐户的余额
 * gettransaction				获取帐户的交易记录
 * 
 * ---交易相关
 * TODO ···
 * 
 * @author ln
 *
 */
public interface RPCService {
	
	/**
	 * 获取区块的数量
	 * @return String
	 */
	long getBlockCount();
	
	/**
	 * 获取最新区块的高度 
	 * @return String
	 */
	long getBestBlockHeight();
	
	/**
	 * 获取最新区块的hash
	 * @return String
	 */
	String getBestBlockHash();
	
	/**
	 * 通过高度获取区块hash
	 * @param height
	 * @return String
	 */
	String getBlockHashByHeight(long height);
	
	/**
	 * 通过区块的hash或者高度获取区块的头信息
	 * @param hashOrHeight
	 * @return JSONObject
	 * @throws JSONException 
	 */
	JSONObject getBlockHeader(String hashOrHeight) throws JSONException;
	
	/**
	 * 通过区块的hash或者高度获取区块的完整信息
	 * @param hashOrHeight
	 * @return JSONObject
	 * @throws JSONException 
	 */
	JSONObject getBlock(String hashOrHeight) throws JSONException;

	/**
	 * 通过hash获取一个分叉块
	 * @param hash
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject getForkBlock(String hash) throws JSONException;
	
	// --- 内存池
	String getmempoolinfo (String count);		//获取内存里的count条交易
	 // --- 帐户
	
	String getaccountpubkeys();			//获取帐户的公钥
	String dumpprivateseed();				//备份私钥种子，同时显示帐户的hash160

	/**
	 * 创建一个普通账户
	 * @return JSONObject
	 * @throws JSONException
	 * @throws IOException 
	 */
	JSONObject newAccount() throws JSONException, IOException;

	/**
	 * 创建count个普通账户
	 * @return JSONObject
	 * @throws JSONException
	 * @throws IOException
	 */
	JSONObject newAccount(int count) throws JSONException, IOException;
	/**
	 * 创建一个认证账户
	 * @param mgpw
	 * @param trpw
	 * @param body
	 * @param certpw 
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject newCertAccount(String mgpw, String trpw, AccountBody body, String certpw,String managerAddress) throws JSONException;
	
	/**
	 * 修改认证账户信息
	 * @param body
	 * @param mgpw
	 * @param address
	 * @return JSONObject
	 * @throws JSONException 
	 */
	JSONObject updateCertAccount(AccountBody body, String mgpw, String address) throws JSONException;

	/**
	 * 吊销认证账户信息
	 * @param revokeAddress
	 * @param mgpw
	 * @param address
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject revokeCertAccount(String revokeAddress, String mgpw,String address) throws JSONException;

	/**
	 * 认证账户修改密码
	 * @param oldMgpw
	 * @param newMgpw
	 * @param newTrpw
	 * @param address
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject certAccountEditPassword(String oldMgpw, String newMgpw, String newTrpw, String address) throws JSONException;
	
	/**
	 * 获取帐户列表
	 * @return JSONArray
	 * @throws JSONException
	 */
	JSONArray getAccounts() throws JSONException;
	
	/**
	 * 获取账户的余额
	 * @param address 
	 * @return Coin[]
	 */
	Coin[] getAccountBalance(String address);

	/**
	 * 获取所有账户的总余额
	 * @param address
	 * @return Coin[]
	 */
	Coin[] getTotalBalance();
	/**
	 * 获取账户的信用
	 * @param address 
	 * @return long
	 * @throws VerificationException 
	 */
	long getAccountCredit(String address) throws VerificationException;
	
	/**
	 * 获取账户的详细信息
	 * @param address 
	 * @return long
	 * @throws JSONException  VerificationException
	 */
	JSONObject getAccountInfo(String address) throws JSONException, VerificationException;
	
	/**
	 * 获取帐户的交易记录
	 * @param address 
	 * @return JSONArray
	 * @throws JSONException 
	 */
	JSONArray getTransaction(String address) throws JSONException;

	/**
	 * 获取帐户的代币交易记录
	 * @param height 区块高度
	 * @param confirm 确认高度
	 * @param address
	 * @return JSONArray
	 * @throws JSONException
	 */
	JSONArray getTransferTx(Long height, Long confirm, String address) throws JSONException;

	/**
	 * 获取交易记录
	 * @param count
	 * @param confirm
	 * @param address
	 * @return
	 * @throws JSONException
	 */
	JSONArray listtransactions(Integer limit, Integer confirm, String address) throws JSONException;

	/**
	 * 通过交易hash获取条交易详情
	 * @param txid
	 * @return JSONObject
	 * @throws JSONException 
	 */
	JSONObject getTx(String txid) throws JSONException;
	
	/**
	 * 认证账户创建商品
	 * @param address
	 * @param product
	 * @param certpw
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject createProduct(Product product, String certpw, String address) throws JSONException;
	
	/**
	 * 认证账户创建防伪码
	 * @param productTx 商品id
	 * @param count 数量
	 * @param sources 来源
	 * @param reward 奖励
	 * @param trpw 账户交易密码
	 * @param address	账户地址
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject createAntifake(String productTx, int count, JSONArray sources, Coin reward, String trpw, String address) throws JSONException;

	/**
	 *	资产注册
	 * @param name 资产名称
	 * @param description 资产描述
	 * @param code 资产代号
	 * @param logo 资产图标
	 * @param remark 资产描述
	 * @return
	 * @throws JSONException
	 */
	JSONObject regAssets(String name, String description, String code, String logo, String remark, String address, String password) throws JSONException;

	/**
	 * 获取资产注册列表
	 * @return
	 * @throws JSONException
	 */
	JSONObject getAssetsRegList() throws JSONException;

	/**
	 * 资产发行
	 * @param code
	 * @param receiver
	 * @param amount
	 * @param address
	 * @param password
	 * @return
	 * @throws JSONException
	 */
	JSONObject assetsIssue(String code, String receiver, Long amount,String remark, String address, String password) throws JSONException;

	/**
	 * 获取资产发行的列表
	 * @param code
	 * @return
	 * @throws JSONException
	 */
	JSONObject getAssetsIssueList(String code) throws JSONException;

	/**
	 * 获取我的资产账户列表
	 * @return
	 * @throws Exception
	 */
	JSONObject getMineAssets(String address, String password) throws JSONException;

	/**
	 *  资产转让
	 * @param code
	 * @param receiver
	 * @param amount
	 * @param remark
	 * @param address
	 * @param password
	 * @return
	 * @throws JSONException
	 */
	JSONObject assetsTransfer(String code, String receiver, Long amount,String remark, String address, String password) throws JSONException;

	/**
	 * 获取共识节点列表
	 * @return JSONArray
	 * @throws JSONException 
	 */
	JSONArray getConsensus() throws JSONException;
	
	/**
	 * 获取共识节点数量
	 * @return JSONObject
	 * @throws JSONException 
	 */
	JSONObject getConsensusCount() throws JSONException;

	/**
	 * 查看当前共识状态
	 * @return JSONObject
	 * @throws JSONException 
	 */
	JSONObject getConsensusStatus() throws JSONException;
	
	/**
	 * 注册共识
	 * @param password 			当前申请账户的账户密码
	 * @param consensusAddress 	指定共识账户
	 * @return JSONObject
	 * @throws JSONException 
	 */
	JSONObject regConsensus(String password, String consensusAddress) throws JSONException;

	/**
	 * 获取当前注册共识所需费用
	 * @return
	 * @throws JSONException
	 */
	JSONObject regconsensusFee() throws JSONException;

	/**
	 * 退出共识
	 * @param password
	 * @return JSONObject
	 * @throws JSONException 
	 */
	JSONObject remConsensus(String rem,String password) throws JSONException;

	/**
	 * 获取连接节点信息
	 * @return JSONObject
	 */
	JSONObject getPeers() throws JSONException;
	
	/**
	 * 获取连接节点数量
	 * @return JSONObject
	 */
	JSONObject getPeerCount() throws JSONException;

	/**
	 * 加密钱包
	 * @param password 
	 * @return JSONObject
	 */
	JSONObject encryptWallet(String password) throws JSONException;

	/**
	 * 修改密码
	 * @return JSONObject
	 * @throws JSONException 
	 */
	JSONObject changePassword(String oldPassword, String newPassword) throws JSONException;

	/**
	 * 锁仓
	 * @param money
	 * @param lockTime
	 * @param address
	 * @param password
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject lockMoney(Coin money, long lockTime, String address, String password, String remark)throws JSONException;

	/**
	 * 发送交易
	 * @param toAddress
	 * @param money
	 * @param address 
	 * @param password 
	 * @param remark 
	 * @param passwordOrRemark
	 * @return JSONObject
	 * @throws JSONException 
	 */
	JSONObject sendMoney(String toAddress, String money, String address, String password, String remark, String passwordOrRemark) throws JSONException;

	/**
	 * 发送交易
	 * @param toaddressAndCoins[{toaddress_1,coin_1},toaddress_2,coin_2},...]
	 * @param remark
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject sendMoneyToAddress(JSONArray toaddressAndCoins,String pass,String remark) throws JSONException;

	/**
	 * 锁仓奖励
	 * @param toAddress
	 * @param money
	 * @param address
	 * @param password
	 * @param remark
	 * @param lockTimeStr
	 * @return
	 * @throws JSONException
	 */
	JSONObject lockReward(String toAddress, Coin money, String address, String password, String remark, long lockTime) throws JSONException;

	/**
	 * 广播交易
	 * @param txContent
	 * @return JSONObject
	 * @throws JSONException 
	 */
	JSONObject broadcast(String txContent) throws JSONException;

	/**
	 * 广播交易
	 * @param amount
	 * @param privateKey
	 * @param toAddress
	 * @param remark
	 * @return
	 * @throws JSONException
	 */
	JSONObject broadcastTransferTransaction(String amount,String privateKey, String toAddress, String remark, JSONArray jsonArray) throws JSONException;

	/**
	 * 广播交易 - 交易内容存放在文件里面
	 * @param filepath
	 * @return JSONObject
	 * @throws JSONException 
	 */
	JSONObject broadcastfromfile(String filepath) throws JSONException;

	/**
	 * 通过防伪码查询防伪码相关的所有信息
	 * @param antifakeCode
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject queryAntifake(String antifakeCode) throws JSONException;

	/**
	 * 把防伪码绑定到产品
	 * @param antiCode
	 * @param productHash
	 * @param trpw
	 * @param address
	 * @return
	 */
	JSONObject bindAntifake(String antiCode,String productHash,String trpw,String address)throws JSONException;
	/**
	 * 防伪码验证
	 * @param params
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject verifyAntifake(JSONArray params) throws JSONException;

	/**
	 * 添加防伪码流转信息
	 * @param antifakeCode				防伪码
	 * @param tag						流转信息标签
	 * @param content					流转信息内容
	 * @param address 					地址
	 * @param privateKeyOrPassword		私钥或者地址密码，当地址为空，这个值不为空之代表私钥，否则代表密码
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject addCirculation(String antifakeCode, String tag, String content, String address,
			String privateKeyOrPassword) throws JSONException;

	/**
	 * 查询防伪码流转信息
	 * @param antifakeCode
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject queryCirculations(String antifakeCode) throws JSONException;

	/**
	 * 查询防伪码流转次数
	 * @param antifakeCode
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject queryCirculationCount(String antifakeCode) throws JSONException;
	
	/**
	 * 查询防伪码流转次数
	 * @param antifakeCode
	 * @param address
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject queryCirculationCount(String antifakeCode, String address) throws JSONException;

	/**
	 * 防伪码转让
	 * @param antifakeCode		防伪码
	 * @param receiver			接收人
	 * @param remark			备注
	 * @param address			转让者账户
	 * @param privateKeyOrPassword	转让者私钥或者账户密码
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject transferAntifake(String antifakeCode, String receiver, String remark, String address,
			String privateKeyOrPassword) throws JSONException;

	/**
	 * 查询防伪码转让记录
	 * @param antifakeCode
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject queryTransfers(String antifakeCode) throws JSONException;

	/**
	 * 查询防伪码转让次数
	 * @param antifakeCode
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject queryTransferCount(String antifakeCode) throws JSONException;

	/**
	 * 查询防伪码拥有者
	 * @param antifakeCode
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject queryAntifakeOwner(String antifakeCode) throws JSONException;

	/**
	 * 认证商家关联子账户
	 * @param relevancer
	 * @param alias
	 * @param content
	 * @param trpw
	 * @param address
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject relevanceSubAccount(String relevancer, String alias, String content, String trpw, String address) throws JSONException;

	/**
	 * 解除子账户的关联
	 * @param relevancer
	 * @param hashId
	 * @param trpw
	 * @param address
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject removeSubAccount(String relevancer, String hashId, String trpw, String address) throws JSONException;
	
	/**
	 * 获取认证商家子账户列表
	 * @param address
	 * @return
	 * @throws JSONException
	 */
	JSONObject getSubAccounts(String address) throws JSONException;

	/**
	 * 获取认证商家子账户数量
	 * @param address
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject getSubAccountCount(String address) throws JSONException;
	
	/**
	 * 检查是否是商家的子账户
	 * @param certAddress
	 * @param address
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject checkIsSubAccount(String certAddress, String address) throws JSONException;

	/**
	 * 通过别名获取账户
	 * @param alias
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject getAccountByAlias(String alias) throws JSONException;

	/**
	 * 通过账户获取别名
	 * @param account
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject getAliasByAccount(String account) throws JSONException;

	/**
	 * 通过公钥得到地址
	 * @param pubkey
	 * @return JSONObject
	 * @throws JSONException
	 */
	JSONObject getAddressByPubKey(String pubkey) throws JSONException;

	/**
	 * 获取私钥
	 * @param address
	 * @param password
	 * @return JSONObject
	 */
	JSONObject getPrivatekey(String address, String password) throws JSONException;

	/**
	 * 重建数据
	 * @return JSONObject
	 */
	JSONObject resetData() throws JSONException;

	/**
	 * 解锁钱包
	 * @param password
	 * @param timeSec
	 * @return JSONObject
	 */
	JSONObject unlockWallet(String passwd,int timeSec) throws JSONException;

	/**
	 * 立即锁定钱包
	 * @return JSONObject
	 */
	JSONObject lockWallet() throws JSONException;


	/**
	 * 检查地址是否合法
	 * @return JSONObject
	 */
	JSONObject validateAddress(String address) throws JSONException;

	JSONObject importPriKey(String prikey) throws JSONException;
}