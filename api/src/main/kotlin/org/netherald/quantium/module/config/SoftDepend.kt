package org.netherald.quantium.module.config

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class SoftDepend(vararg val values : String)
