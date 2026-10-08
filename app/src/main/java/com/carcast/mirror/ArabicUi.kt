package com.carcast.mirror

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

val LocalArabic = compositionLocalOf { false }

private val arabicTranslations = mapOf(
    "Cast" to "البث",
    "Receive" to "الاستقبال",
    "Browser" to "المتصفح",
    "Car" to "السيارة",
    "Help" to "المساعدة",
    "Settings" to "الإعدادات",
    "Diagnostics" to "التشخيص",
    "Confirm pairing code" to "تأكيد رمز الاقتران",
    "Pairing code" to "رمز الاقتران",
    "Continue" to "متابعة",
    "Cancel" to "إلغاء",
    "Local network access" to "الوصول إلى الشبكة المحلية",
    "Allow local network access" to "السماح بالوصول إلى الشبكة المحلية",
    "Nearby displays" to "الشاشات القريبة",
    "No displays found yet" to "لم يتم العثور على شاشات بعد",
    "Use Android System Cast" to "استخدام بث نظام Android",
    "Browser receiver" to "مستقبل المتصفح",
    "Start Browser Receiver" to "بدء مستقبل المتصفح",
    "Stop Casting" to "إيقاف البث",
    "Reconnect" to "إعادة الاتصال",
    "Approve this TV connection" to "الموافقة على اتصال التلفاز",
    "Approve & Start" to "موافقة وبدء",
    "Reject" to "رفض",
    "Open on your TV" to "افتح على التلفاز",
    "Car mode" to "وضع السيارة",
    "Help & FAQ" to "المساعدة والأسئلة الشائعة",
    "Privacy" to "الخصوصية",
    "Privacy options" to "خيارات الخصوصية",
    "Advertising" to "الإعلانات",
    "Language" to "اللغة",
    "English" to "الإنجليزية",
    "Arabic" to "العربية",
    "Arabic language" to "اللغة العربية",
    "Diagnostics" to "التشخيص",
    "Connection" to "الاتصال",
    "Video" to "الفيديو",
    "Network" to "الشبكة",
    "Audio" to "الصوت",
    "Manual receiver" to "مستقبل يدوي",
    "IP address" to "عنوان IP",
    "Port" to "المنفذ",
    "Start receiver" to "بدء المستقبل",
    "Stop receiver" to "إيقاف المستقبل",
    "CONNECT" to "اتصال",
    "DECLINE" to "رفض",
    "CLEAR" to "مسح",
    "COPY DEBUG REPORT" to "نسخ تقرير التشخيص",
    "What will be shared" to "ما سيتم مشاركته",
    "Share device audio" to "مشاركة صوت الجهاز",
    "Actual stream" to "البث الفعلي",
    "Supported paths" to "المسارات المدعومة",
    "Why can’t I see a receiver?" to "لماذا لا أرى مستقبلاً؟",
    "Why is audio unavailable?" to "لماذا الصوت غير متاح؟",
    "How do I recover a failed session?" to "كيف أستعيد جلسة فاشلة؟",
    "What does CarCast store?" to "ماذا يخزن CarCast؟"
)

@Composable
fun A(text: String): String = if (LocalArabic.current) arabicTranslations[text] ?: text else text
