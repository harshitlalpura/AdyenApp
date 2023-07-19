package com.adyen.sampleapp

import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.adyen.ipp.InPersonPayments
import com.adyen.ipp.core.ext.decodeFromBase64String
import com.adyen.ipp.payment.PaymentInterface
import com.adyen.ipp.payment.PaymentInterfaceType
import com.adyen.ipp.payment.TransactionRequest
import com.adyen.sampleapp.databinding.FragmentPaymentBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import logcat.logcat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class PaymentSampleAppFragment : Fragment() {

    private val logTag = "Transaction"

    private lateinit var binding: FragmentPaymentBinding
    private val resultLauncher = InPersonPayments.registerForPaymentResult(this) { paymentResult ->
        val resultText = if (paymentResult.success) "Payment Successful" else "Payment Failed"
        Toast.makeText(requireContext(), resultText, Toast.LENGTH_LONG).show()
        logcat(tag = logTag) { "Result: \n ${paymentResult.data.decodeFromBase64String()}" }
    }

    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonPayNyc1.setOnClickListener {
            uiScope.launch {
                InPersonPayments.getPaymentInterface(PaymentInterfaceType.CardReader())
                    .fold(
                        onSuccess = { nyc1Interface ->
                            startPayment(nyc1Interface)
                        },
                        onFailure = {
                            Toast.makeText(
                                requireContext(),
                                R.string.toast_no_bt_permissions,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
            }
        }
        binding.buttonPayT2p.setOnClickListener {
            uiScope.launch {
                InPersonPayments.getPaymentInterface(PaymentInterfaceType.TapToPay)
                    .fold(
                        onSuccess = { t2pInterface ->
                            startPayment(t2pInterface)
                        },
                        onFailure = {
                            Toast.makeText(
                                requireContext(),
                                R.string.toast_t2p_interface_creation_failed,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
            }
        }
    }

    private suspend fun startPayment(paymentInterface: PaymentInterface<*>) {
        val nexoRequest: String = generateNexoRequest(
            requestedAmount = "5",
            currency = "USD",
            poiId = InPersonPayments.getInstallationId()
        )
        logcat(logTag) { "NexoRequest:\n$nexoRequest" }

        InPersonPayments.performTransaction(
            context = requireContext(),
            paymentInterface = paymentInterface,
            transactionRequest = TransactionRequest.create(nexoRequest).getOrThrow(),
            paymentLauncher = resultLauncher,
            authenticationServiceClass = MyAuthenticationService::class.java,
        )
    }

    companion object {

        private val DATE_FORMAT =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

        private fun generateNexoRequest(
            serviceId: String = UUID.randomUUID().toString(),
            saleId: String = "AndroidSampleApp",
            transactionID: String = "SampleApp-AndroidTx",
            poiId: String,
            currency: String,
            requestedAmount: String,
        ): String {

            val timeStamp = DATE_FORMAT.format(Date())
            val maxServiceIdSize = 10

            return """
                |{
                |  "SaleToPOIRequest": {
                |    "MessageHeader": {
                |      "ProtocolVersion": "3.0",
                |      "MessageClass": "Service",
                |      "MessageCategory": "Payment",
                |      "MessageType": "Request",
                |      "ServiceID": "${serviceId.take(maxServiceIdSize)}",
                |      "SaleID": "$saleId",
                |      "POIID": "$poiId"
                |    },
                |    "PaymentRequest": {
                |      "SaleData": {
                |        "SaleTransactionID": {
                |          "TransactionID": "$transactionID",
                |          "TimeStamp": "$timeStamp"
                |        }
                |      },
                |      "PaymentTransaction": {
                |        "AmountsReq": {
                |          "Currency": "$currency",
                |          "RequestedAmount": $requestedAmount
                |        }
                |      }
                |    }
                |  }
                |}
            """.trimMargin("|")
        }
    }
}