package com.ksinfra.clawapk.domain.port

interface NotificationPort {
    fun showMessageNotification(title: String, body: String)
    fun showCronNotification(jobName: String, message: String)
}
