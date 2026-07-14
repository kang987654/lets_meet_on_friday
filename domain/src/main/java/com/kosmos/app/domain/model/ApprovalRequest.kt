package com.kosmos.app.domain.model

import com.kosmos.app.core.security.ApprovalRules

data class ApprovalRequest(
    val id: String,
    val actionType: ApprovalRules.ActionType,
    val title: String,
    val description: String,
    val payload: ActionPayload,
    val isPending: Boolean = true
)
