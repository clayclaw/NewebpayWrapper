import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToMap
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit
import org.apache.commons.codec.digest.DigestUtils
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

@Serializable
data class NewebpayMPGData(
    /**
     * 藍新金流商店代號
     */
    @SerialName("MerchantID")
    @Required
    var merchantId: String,
    /**
     * 交易資料 AES 加密
     * 將交易資料參數透過商店 Key 及 IV 進行 AES 加密
     */
    @SerialName("TradeInfo")
    @Required
    var tradeInfo: String,
    /**
     * 交易資料 SHA256 加密
     * 將交易資料經過上述 AES 加密過的字串透過商店 Key 及 IV 進行 SHA256 加密
     */
    @SerialName("TradeSha")
    @Required
    var tradeSha: String,
    /**
     * 串接程式版本
     */
    @SerialName("Version")
    @Required
    var version: String,
    /**
     * 加密模式
     * 設定加密模式
     * 1 = 加密模式 AES/GCM
     * 0 或者未有此參數 = 原加密模式 AES/CBC/PKCS7Padding
     */
    @SerialName("EncryptType")
    private var encryptType: Int? = null
) {

    fun setEncryptType(type: NewebpayEncryptType) {
        this.encryptType = type.parameterData
    }

}

/**
 * 【備註 1】
 * 當下列所有參數 CREDIT、ANDROIDPAY、SAMSUNGPAY、InstFlag、CreditRed、UNIONPAY、WEBATM、VACC、CVS、BARCODE、CVSCOM、
 * ESUNWALLET、TAIWANPAY、LINEPAY、EZPAY、EZPWECHAT、EZPALIPAY 皆未以 API 指定啟用時，則以商店設定值為準。
 *
 * 【備註 2】
 * NotifyURL 及 ReturnURL 參數補充說明：
 * 1. 商店欲接收支付完成訊息，請務必設定 NotifyURL。
 * 2. 商店欲支付完成後引導消費者回商店網頁，請務必設定 ReturnURL。
 * 3. NotifyURL 及 ReturnURL 可以下列兩種方式設定方式如下：
 * (1) API 參數設定：每筆交易建立時以 API 參數提供。
 * (2) 商店於藍新金流平台設定：於藍新金流平台【會員中心】單元，【商店管理】
 * 目錄【商店資料設定】子目錄，於該商店詳細資料中設定 API 應用 URL。
 * (3) 當兩種方式皆有設定時，會以 API 參數設定為主。
 * 4. ReturnURL 與 NotifyURL 均會攜帶回應參數回傳，請勿設定相同網址進而造成交易誤判。
 * 例：ReturnURL 與 NotifyURL 設定相同網址，則該網址會接收到兩次付款完成資訊，但實際付款完成只有㇐次，將會影響商店出貨及帳務的正確性。
 * 5. 玉山 Wallet 及台灣 Pay 目前不支援 ReturnURL，若要接收交易資訊請帶 NotifyURL。
 *
 * 【備注 3】
 * 本Wrapper暫不提供國民旅遊卡交易、信用卡快速結帳參數
 */
@Serializable
data class NewebpayTradeInfo(
    /**
     * 藍新金流商店代號
     */
    @SerialName("MerchantID")
    @Required
    val merchantId: String,
    /**
     * 回傳格式
     * 可以是 JSON 或者 String
     */
    @SerialName("RespondType")
    @Required
    val respondType: String,
    /**
     * 時間戳記
     * 須確實帶入自 Unix 紀元到當前時間的秒數以避免交易失敗。(容許誤差值 1200 秒)
     */
    @SerialName("TimeStamp")
    @Required
    val timeStamp: String = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toString(),
    /**
     * 串接程式版本
     */
    @SerialName("Version")
    @Required
    val version: String = DefaultNewebpayOption.APP_VERSION,
    /**
     * 設定 MPG 頁面顯示的文字語系
     * 可以填 en zh-tw jp
     */
    @SerialName("LangType")
    val langType: String? = null,
    /**
     * 商店訂單編號
     * 商店自訂訂單編號，限英、數字、”_ ”格式
     * 例：201406010001
     * ⾧度限制為 30 字元
     * 同㇐商店中此編號不可重覆
     */
    @SerialName("MerchantOrderNo")
    @Required
    val merchantOrderNo: String,
    /**
     * 訂單金額
     * 純數字不含符號，例：1000
     * 幣別：新台幣
     */
    @SerialName("Amt")
    @Required
    val amount: Int,
    /**
     * 商品資訊
     * 限制⾧度為 50 字元
     * UTF-8編碼
     * 請勿使用斷行符號、單引號等特殊符號，避免無法顯示完整付款頁面
     * 若使用特殊符號，系統將自動過濾
     */
    @SerialName("ItemDesc")
    @Required
    val itemDescription: String,
    /**
     * 交易限制秒數
     * 限制交易的秒數，當秒數倒數至 0 時，交易當做失敗
     * 秒數下限為 60 秒，當秒數介於 1~59 秒時，會以 60 秒計算
     * 秒數上限為 900 秒，當超過 900 秒時，會以 900 秒計算
     * 若未帶此參數，或是為 0 時，會視作為不啟用交易限制秒數
     */
    @SerialName("TradeLimit")
    val tradeTimeLimit: Int? = null,
    /**
     * 繳費有效期限 (適用於非即時交易)
     * 格式為 date('Ymd') ，例：20140620
     * 此參數若為空值，系統預設為 7 天。自取號時間起算至第 7 天 23:59:59
     * 例：2014-06-23 14:35:51 完成取號，則繳費有效期限為 2014-06-29 23:59:59
     * 可接受最大值為 180 天
     */
    @SerialName("ExpireDate")
    val expireDate: String? = null,
    /**
     * 支付完成返回商店網址
     * 交易完成後，以 Form Post 方式導回商店頁面
     * 若支付工具為玉山 Wallet、台灣 Pay 或本欄位為空值，於交易完成後，消費者將停留在藍新金流付款或取號結果頁面
     * 只接受 80 與 443 Port
     */
    @SerialName("ReturnURL")
    val returnURL: String? = null,
    /**
     * 支付通知網址
     * 以幕後方式回傳給商店相關支付結果資料
     * 只接受 80 與 443 Port
     */
    @SerialName("NotifyURL")
    val notifyURL: String? = null,
    /**
     * 商店取號網址
     * 系統取號後以 form post 方式將結果導回商店指定的網址
     * 此參數若為空值，則會顯示取號結果在藍新金流頁面
     */
    @SerialName("CustomerURL")
    val customerURL: String? = null,
    /**
     * 返回商店網址
     * 在藍新支付頁或藍新交易結果頁面上所呈現之返回鈕，我方將依據此參數之設定值進行設定，引導商店消費者依以此參數網址返回商店指定的頁面
     * 此參數若為空值時，則無返回鈕
     */
    @SerialName("ClientBackURL")
    val clientBackURL: String? = null,
    /**
     * 付款人電子信箱
     * 於交易完成或付款完成時，通知付款人使用
     */
    @SerialName("Email")
    val email: String? = null,
    /**
     * 設定於 MPG 頁面，付款人電子信箱欄位是否開放讓付款人修改
     * 當未提供此參數時，將預設為可修改
     */
    @SerialName("EmailModify")
    val allowModifyEmail: Int? = null,
    /**
     * 設定是否需要登入藍新金流會員
     */
    @SerialName("LoginType")
    val requireLogin: Int? = null,
    /**
     * 商店備註
     * 若有提供此參數，將會於 MPG 頁面呈現商店備註內容。
     * 限制⾧度為 300 字
     */
    @SerialName("OrderComment")
    val orderComment: String? = null,
    /**
     * 設定是否啟用信用卡㇐次付清支付方式
     */
    @SerialName("CREDIT")
    val enableCreditCard: Int? = null,
    /**
     * 信用卡分期付款啟用
     * 此欄位值=1 時，即代表開啟所有分期期別，且不可帶入其他期別參數
     * 此欄位值為下列數值時，即代表開啟該分期期別。
     * 3=分 3 期功能
     * 6=分 6 期功能
     * 12=分 12 期功能
     * 18=分 18 期功能
     * 24=分 24 期功能
     * 30=分 30 期功能
     *
     * 同時開啟多期別時，將此參數用”，”(半形)分隔，例如：3,6,12，代表開啟 分 3、6、12 期的功能
     * 此欄位值=0 或無值時，即代表不開啟分期
     */
    @SerialName("InstFlag")
    val creditCardInstFlag: String? = null,
    /**
     * 設定是否啟用信用卡紅利支付方式
     */
    @SerialName("CreditRed")
    val enableCreditCardRed: Int? = null,
    /**
     * 設定是否啟用 Google Pay 支付方式
     */
    @SerialName("ANDROIDPAY")
    val enableGooglePay: Int? = null,
    /**
     * 設定是否啟用 Samsung Pay 支付方式
     */
    @SerialName("SAMSUNGPAY")
    val enableSamsungPay: Int? = null,
    /**
     * 設定是否啟用 Line Pay 支付方式
     */
    @SerialName("LINEPAY")
    val enableLinePay: Int? = null,
    /**
     * Line Pay啟用的時候視乎需求填入的圖片參數
     * 此連結的圖檔將顯示於 LinePay 付款前的產品圖片區，若無產品圖檔連結網址，會使用藍新系統預設圖檔
     * 圖片建議使用 84*84 像素(若大於或小於該尺寸有可能造成破圖或變形)
     * 圖片類型只支援jpg及png
     */
    @SerialName("ImageUrl")
    val linePayImageURL: String? = null,
    /**
     * 設定是否啟用 Union Pay 支付方式
     */
    @SerialName("UNIONPAY")
    val enableUnionPay: Int? = null,
    /**
     * 設定是否啟用 Web ATM 支付方式
     * 當該筆訂單金額超過 5 萬元時，即使此參數設定為啟用，MPG 付款頁面仍不會顯示此支付方式選項
     */
    @SerialName("WEBATM")
    val enableWebATM: Int? = null,
    /**
     * 設定是否啟用 ATM 轉帳支付方式
     * 當該筆訂單金額超過 5 萬元時，即使此參數設定為啟用，MPG 付款頁面仍不會顯示此支付方式選項
     */
    @SerialName("VACC")
    val enableATMTransfer: Int? = null,
    /**
     * 設定是否啟用超商代碼繳費支付方式
     * 當該筆訂單金額小於 30 元或超過 2 萬元時，即使此參數設定為啟用，MPG 付款頁面仍不會顯示此支付方式選項
     */
    @SerialName("CVS")
    val enableCVS: Int? = null,
    /**
     * 設定是否啟用超商條碼繳費支付方式
     * 當該筆訂單金額小於 20 元或超過 4 萬元時，即使此參數設定為啟用，MPG 付款頁面仍不會顯示此支付方式選項
     */
    @SerialName("BARCODE")
    val enableBarcode: Int? = null,
    /**
     * 設定是否啟用玉山 Wallet 支付方式
     */
    @SerialName("ESUNWALLET")
    val enableEsunWallet: Int? = null,
    /**
     * 設定是否啟用台灣 Pay 支付方式
     */
    @SerialName("TAIWANPAY")
    val enableTaiwanPay: Int? = null,
    /**
     * 物流啟用
     * 使用前，須先登入藍新金流會員專區啟用物流並設定退貨門市與取貨人相關資訊
     * 1 = 啟用超商取貨不付款
     * 2 = 啟用超商取貨付款
     * 3 = 啟用超商取貨不付款及超商取貨付款
     * 0 或者未有此參數，即代表不開啟
     * 當該筆訂單金額小於 30 元或大於 2 萬元時，即使此參數設定為啟用，MPG 付款頁面仍不會顯示此支付方式選項
     */
    @SerialName("CVSCOM")
    val cvscomOption: Int? = null,
    /**
     * 設定是否啟用簡單付電子錢包支付方式
     */
    @SerialName("EZPAY")
    val enableEZPay: Int? = null,
    /**
     * 設定是否啟用簡單付微信支付支付方式
     */
    @SerialName("EZPWECHAT")
    val enableEZPayWeChat: Int? = null,
    /**
     * 設定是否啟用簡單付微信支付支付方式
     */
    @SerialName("EZPAYALIPAY")
    val enableEZPayAlipay: Int? = null,
    /**
     * 物流型態
     * 帶入參數值說明：
     * B2C＝超商大宗寄倉(目前僅支援統㇐超商)
     * C2C＝超商店到店(目前僅支援全家)
     *
     * 若商店未帶入此參數，則系統預設值說明如下：
     * a.系統優先啟用［B2C 大宗寄倉］。
     * b.若商店設定中未啟用［B2C 大宗寄倉］，則系統將會啟用［C2C 店到店］。
     * c.若商店設定中，［B2C 大宗寄倉］與［C2C 店到店］皆未啟用，則支付頁面中將不會出現物流選項。
     */
    @SerialName("LgsType")
    val lgsType: String? = null
)

enum class NewebpayEncryptType(
    val parameterData: Int
) {
    AES_GCM(1),
    AES_CBC_PKCS7PADDING(0);

    override fun toString(): String {
        return "${this.parameterData}"
    }
}

enum class NewepayLangType(
    val parameterData: String
) {

    ZH_TW("zh-tw"),
    JAPANESE("jp"),
    ENGLISH("en");

    override fun toString(): String {
        return this.parameterData
    }
}

enum class NewepayRespondType(
    val parameterData: String
) {
    JSON("JSON"),
    STRING("String");

    override fun toString(): String {
        return this.parameterData
    }
}


/**
 * 所有支付方式共同回傳參數
 */
@Serializable
abstract class NewebpayDefaultCallbackResult {
    @SerialName("MerchantID")
    abstract val merchantId: String
    @SerialName("Amt")
    abstract val amount: Int
    @SerialName("TradeNo")
    abstract val tradeNo: String
    @SerialName("PaymentType")
    abstract val paymentType: String
    @SerialName("RespondType")
    abstract val respondType: String
    @SerialName("PayTime")
    abstract val payTime: String
    @SerialName("IP")
    abstract val ipv4: String
    @SerialName("EscrowBank")
    abstract val escrowBank: String
}

/**
 * 信用卡支付回傳參數（㇐次付清、Google Pay、Samaung Pay、國民旅遊卡、銀聯）
 */
@Serializable
abstract class NewebpayCreditCallbackResult: NewebpayDefaultCallbackResult() {
    @SerialName("AuthBank")
    abstract val authBank: String
    @SerialName("RespondCode")
    abstract val respondCode: String
    @SerialName("Auth")
    abstract val auth: String
    @SerialName("Card6No")
    abstract val cardFirst6No: String
    @SerialName("Card4No")
    abstract val cardLast4No: String
    @SerialName("Inst")
    abstract val inst: Int
    @SerialName("InstFirst")
    abstract val instFirst: Int
    @SerialName("InstEach")
    abstract val instEach: Int
    @SerialName("ECI")
    abstract val eci: String
    @SerialName("TokenUseStatus")
    abstract val tokenUseStatus: Int
    @SerialName("RedAmt")
    abstract val redAmount: Int
    @SerialName("PaymentMethod")
    abstract val paymentMethod: String
    @SerialName("DCC_Amt")
    abstract val dccAmount: Float
    @SerialName("DCC_Rate")
    abstract val dccRate: Float
    @SerialName("DCC_Markup")
    abstract val dccMarkup: Float
    @SerialName("DCC_Currency")
    abstract val dccCurrency: String
    @SerialName("DCC_Currency_Code")
    abstract val dccCurrencyCode: Int
}

@ExperimentalSerializationApi
data class NewebpayOption(
    val gatewayURL: String = DefaultNewebpayOption.DEBUG_MPGGATEWAY_LINK,
    val hashKey: String,
    val hashIV: String,
    val merchantId: String,
    val version: String = DefaultNewebpayOption.APP_VERSION,
    val encryptType: NewebpayEncryptType = NewebpayEncryptType.AES_CBC_PKCS7PADDING
) {

    internal val encryptHelper: EncryptHelper = EncryptHelper(this)

    private fun tradeInfoAES(info: NewebpayTradeInfo): String {
        val tradeInfoMap = Properties.encodeToMap(info)
        val query = HttpUtil.http_build_query(tradeInfoMap)
        println("QUERY BUILT: ${query}")
        return encryptHelper.encrypt(query)
    }

    private fun tradeInfoSha(aes: String): String {
        return encryptHelper.sha256encrypt("HashKey=${hashKey}&${aes}&HashIV=${hashIV}")
    }

    fun getData(tradeInfo: NewebpayTradeInfo): NewebpayMPGData {
        val aes256 = tradeInfoAES(tradeInfo)
        val sha256 = tradeInfoSha(aes256)
        // println("AES256: ${aes256}")
        // println("SHA256: ${sha256}")
        return NewebpayMPGData(merchantId, aes256, sha256, version).apply {
            setEncryptType(encryptType)
        }
    }

    fun getHttpForm(data: NewebpayMPGData): String {
        val parameterMap = Properties.encodeToMap(data).toMutableMap()

        // println("PARAMETER MAP: ${parameterMap.entries}")

        // parameterMap.remove("TradeInfo")
        // parameterMap["TradeInfo"] = ""

        return listOf(
            "<form id='newebpay' method='post' action='${gatewayURL}' style=\"display:none;\">",
        ).plus(parameterMap.map {
            "<input type='text' name='${it.key}' value='${it.value}' type=\"hidden\">"
        }).plus(listOf(
            "<input type='submit' value='Submit'>",
            "</form>",
            "<script type=\"text/javascript\">",
            "document.getElementById(\"newebpay\").submit();",
            "</script>"
        )).joinToString("")
    }

}

object DefaultNewebpayOption {
    const val MPGGATEWAY_LINK = "https://core.newebpay.com/MPG/mpg_gateway"
    const val DEBUG_MPGGATEWAY_LINK = "https://ccore.newebpay.com/MPG/mpg_gateway"

    const val APP_VERSION = "2.0"
}

object HttpUtil {

    @JvmStatic
    fun http_build_query(array: Map<String, Any>): String {
        var reString: String = ""
        val it: Iterator<*> = array.mapValues { it.value.toString() }.entries.iterator()
        while (it.hasNext()) {
            val (key, value) = it.next() as Map.Entry<*, *>
            reString += "$key=$value&"
        }
        reString = reString!!.substring(0, reString.length - 1)
        try {
            reString = URLEncoder.encode(reString, "utf-8")
        } catch (e: UnsupportedEncodingException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        reString = reString!!.replace("%3D", "=").replace("%26", "&")
        return reString
    }

}

class EncryptHelper(
    private val option: NewebpayOption
) {

    private val transformation: String = "AES/CBC/PKCS5Padding"
    private val keySpec = SecretKeySpec(option.hashKey.toByteArray(), 0, 32, "AES")
    private val ivParameterSpec = IvParameterSpec(option.hashIV.toByteArray())

    private val ByteArray.asHexUpper: String
        inline get() {
            return this.joinToString(separator = "") {
                String.format("%02X", (it.toInt() and 0xFF))
            }
        }

    private val String.hexAsByteArray: ByteArray
        inline get() {
            return this.chunked(2).map {
                it.uppercase().toInt(16).toByte()
            }.toByteArray()
        }

    fun sha256encrypt(input: String): String {
        return DigestUtils.sha256Hex(input).uppercase()
    }

    fun encrypt(input: String): String {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivParameterSpec)
        val encrypt = cipher.doFinal(input.toByteArray())
        return encrypt.asHexUpper
    }

    fun decrypt(input: String): String {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivParameterSpec)
        val encrypt = cipher.doFinal(input.hexAsByteArray)
        return String(encrypt)
    }
}
