# Eagle Files Manager — مدير ملفات محلي لأندرويد 14 وما فوق

تطبيق Android أصلي بلغة Kotlin وJetpack Compose لإدارة الملفات والوسائط والمستندات محليًا. يدعم Android 14 (API 34) وما بعده، ويبنى باستخدام Android SDK 36.

## ⬇️ تنزيل APK

<p align="center">
  <a href="https://github.com/areagleegy/Eagle-files-manager/releases/latest/download/app-debug.apk">
    <img src="https://img.shields.io/badge/تنزيل-APK%20مباشر-2ea44f?style=for-the-badge&logo=android" alt="تنزيل APK مباشرة" />
  </a>
</p>

يعمل زر التنزيل المباشر بعد نشر أول GitHub Release وإرفاق `app-debug.apk`. لنشره دون إنشاء Tag يدويًا: افتح [سير عمل البناء](https://github.com/areagleegy/Eagle-files-manager/actions/workflows/android-apk.yml)، اضغط **Run workflow** مع اختيار `main`، واجعل `publish_release` = `true` و`release_tag` = `v1.2.0`، ثم ابدأ التنفيذ. بعد نجاح الاختبارات والبناء، ينشئ السير عمل الإصدار ويرفق APK تلقائيًا. للبناء والاختبار فقط، اترك `publish_release` = `false`.

> **تنبيه:** هذه نسخة Debug للاختبار وليست إصدارًا إنتاجيًا موقّعًا. قد تحتاج إلى السماح للمتصفح أو مدير الملفات بتثبيت التطبيقات من مصادر غير معروفة.

## الميزات

- مدير ملفات: تصفح المجلدات، بحث وفرز، إنشاء مجلد، إعادة تسمية، حذف، نسخ ونقل.
- عارض صور للأنواع الشائعة مثل JPG وPNG وGIF وWebP وBMP وHEIC/HEIF وAVIF عندما يدعمها النظام.
- مشغل صوت داخلي ومشغل فيديو داخلي؛ دعم الترميز يعتمد على إمكانات Android والجهاز.
- محرك ZIP: عرض محتويات الأرشيف، إنشاء ZIP من العناصر المحددة، واستخراج مع فحوص Zip Slip وحدود للحجم وعدد العناصر، ومنع الكتابة فوق الملفات الموجودة.
- قارئ PDF داخلي مع التنقل بين الصفحات، وقارئ للملفات النصية بحد عرض 1 ميبيبايت. ملفات Office وغيرها يمكن فتحها بتطبيق مثبت محليًا.
- واجهة عربية RTL مع فلاتر للصور والفيديو والصوت والمستندات والأرشيف.

## الخصوصية

- لا يتضمن Manifest إذن INTERNET أو أذونات الشبكة.
- لا توجد إعلانات أو تحليلات أو حسابات أو مزامنة سحابية مضافة.
- يختار المستخدم مجلدًا بنفسه عبر منتقي Android، ويحتفظ التطبيق بإذن الوصول إلى ذلك المجلد فقط.
- عمليات إدارة الملفات والأرشفة تعمل محليًا؛ ما يفتحه المستخدم في تطبيق خارجي يخضع لذلك التطبيق.

## قيود معلومة

- الأرشيفات المدعومة حاليًا ZIP فقط؛ RAR و7z غير مضافين.
- لا توجد محررات أو عارضات Office متكاملة داخل التطبيق.
- دعم بعض الصور/الصوت/الفيديو يعتمد على تنسيق الملف وما يدعمه الجهاز.
- يجب نجاح GitHub Actions واختبار APK على جهاز فعلي قبل اعتبار التطبيق جاهزًا للإصدار.

## إعدادات البناء

- Application ID: `com.offline.filemanager`
- `minSdk = 34` (Android 14)
- `targetSdk = 36`
- JDK 17، Gradle 8.13، Android Gradle Plugin 8.13.2، Kotlin 2.2.20

- [سير عمل البناء والنشر](https://github.com/areagleegy/Eagle-files-manager/actions/workflows/android-apk.yml)
- [صفحة الإصدارات](https://github.com/areagleegy/Eagle-files-manager/releases)
