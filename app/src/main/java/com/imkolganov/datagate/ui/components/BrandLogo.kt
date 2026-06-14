package com.imkolganov.datagate.ui.components

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.imkolganov.datagate.R

@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.login_logo_cd),
) {
    val onDarkBackground = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val logoRes = if (onDarkBackground) {
        R.drawable.ic_brand_logo_on_dark_bg
    } else {
        R.drawable.ic_brand_logo_on_light_bg
    }
    Image(
        painter = painterResource(logoRes),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}
