package com.imkolganov.datagate.auth

fun TokenStore.getAuthInfo(): AuthInfo {
    val claims = JwtClaimsReader.read(getAccessToken())
    return AuthInfo(
        userId = claims.userId,
        externalId = claims.externalId,
        role = claims.role,
        displayName = claims.displayName,
        email = claims.email,
        avatarUrl = claims.avatarUrl
    )
}

fun TokenStore.getJwtClaims(): JwtClaims {
    return JwtClaimsReader.read(getAccessToken())
}
