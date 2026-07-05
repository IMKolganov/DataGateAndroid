package com.imkolganov.datagate.configs

object ApiConfig {
    const val GOOGLE_LOGIN_PATH = "/api/auth/google-login"
    const val REFRESH_PATH = "/api/auth/refresh"
    const val REGISTER_PATH = "/api/auth/register"
    const val LOGIN_PATH = "/api/auth/login"
    const val EMAIL_REQUEST_CONFIRMATION_PATH = "/api/auth/email/request-confirmation"
    const val EMAIL_CONFIRM_PATH = "/api/auth/email/confirm"
    const val FORGOT_PASSWORD_PATH = "/api/auth/forgot-password"
    const val RESET_PASSWORD_PATH = "/api/auth/reset-password"

    const val TOTP_VERIFY_LOGIN_PATH = "/api/auth/totp/verify-login"
    const val TOTP_STATUS_PATH = "/api/auth/totp/status"
    const val TOTP_SETUP_PATH = "/api/auth/totp/setup"
    const val TOTP_CONFIRM_PATH = "/api/auth/totp/confirm"
    const val TOTP_DISABLE_PATH = "/api/auth/totp/disable"

    const val FREE_TIER_ACCESS_STATUS_PATH = "/api/auth/free-tier-access/status"
    const val TELEGRAM_REQUEST_ACCOUNT_LINK_CODE_PATH = "/api/auth/telegram/request-account-link-code"

    /** GET …/get-all — V2 list without live metrics (optional). */
    const val API_OPEN_VPN_SERVERS_V2_GET_ALL_PATH = "api/v2/open-vpn-servers/get-all"

    /** Same as legacy v1 `get-all-with-status`: servers + status log + byte/client counts. */
    const val API_OPEN_VPN_SERVERS_V2_GET_ALL_WITH_STATUS_PATH = "api/v2/open-vpn-servers/get-all-with-status"

    /** Full server list + per-server quota flags + [userQuotaPlan] context (mobile Access tab). */
    const val API_OPEN_VPN_SERVERS_V3_GET_ALL_WITH_STATUS_PATH = "api/v3/open-vpn-servers/get-all-with-status"

    const val API_OPEN_VPN_CLIENTS_OVERVIEW_SERIES_PATH = "api/open-vpn-clients/overview/series"
    const val API_OPEN_VPN_CLIENTS_OVERVIEW_SUMMARY_PATH = "api/open-vpn-clients/overview/summary"
    const val API_OPEN_VPN_FILES_ADD_WITH_TOKEN_PATH = "api/open-vpn-files/add-with-token"
    const val API_OPEN_VPN_FILES_DOWNLOAD_FILE_BY_CN_PATH = "api/open-vpn-files/download-file-by-cn"

    const val API_QUOTA_PLANS_GET_ALL = "api/quota-plans/get-all"
    const val API_USER_QUOTA_PLANS_GET_BY_USER_ID_PREFIX = "api/user-quota-plans/get-by-user-id/"
}