package kiwi.argen.junini.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kiwi.argen.junini.gemini.ServerCertificate
import kiwi.argen.junini.gemini.formatFingerprint

/** Asks whether to trust a certificate that doesn't match the one pinned for the host. */
@Composable
fun CertificateChangedView(
    page: PageState.CertificateChanged,
    onTrust: () -> Unit,
    onCancel: () -> Unit,
    contentPadding: PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 480.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Certificate changed", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${page.host} presented a different certificate from the one Junini trusted " +
                        "on an earlier visit. The server may have replaced its certificate, or someone may " +
                        "be intercepting the connection. Nothing has been sent to the server.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                CertificateDetails("Trusted certificate", page.pinned)
                CertificateDetails("New certificate", page.presented)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Button(onClick = onTrust) { Text("Trust new certificate") }
                }
            }
        }
    }
}

@Composable
private fun CertificateDetails(label: String, certificate: ServerCertificate) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            text = formatFingerprint(certificate.fingerprint),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "Expires ${certificate.notAfter.toString().substringBefore('T')}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
