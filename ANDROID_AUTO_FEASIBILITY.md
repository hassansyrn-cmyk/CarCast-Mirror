# Android Auto feasibility

A normal third-party Android Auto app cannot act as a generic MediaProjection receiver for a factory head unit. Android Auto exposes supported app categories and services. The official Android Auto documentation says parked activities on the head unit are limited to supported parked categories, while current documentation identifies games as the Android Auto parked category and video as an Android Automotive OS category. A generic arbitrary phone-screen mirror is therefore not a supported Android Auto integration.

CarCast must not claim compatibility with a factory vehicle Android Auto screen. The legitimate alternatives are a CarCast Receiver installed directly on an Android-powered head unit, a separate receiver device connected to the display, a browser receiver where the browser can reach the local endpoint, or Android's own public system casting handoff for TVs that support Miracast/Wi-Fi Display.

Android Automotive OS is separate from Android Auto. A parked video app for Automotive OS requires the eligible car-app category, driving-state restrictions, adaptive UI, and Play review. It is not a license to inject arbitrary phone pixels into Android Auto.

References: https://developer.android.com/training/cars, https://developer.android.com/training/cars/platforms/android-auto, https://developer.android.com/training/cars/parked/auto, https://developer.android.com/training/cars/parked/video
