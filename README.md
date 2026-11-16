# SnapLite – تطبيق تحميل فيديو وموسيقى للأندرويد

تطبيق أندرويد (Kotlin + Jetpack Compose) يعمل بالكامل على الهاتف، بدون سيرفر.
محرك التحميل هو **yt-dlp** عبر مكتبة `youtubedl-android`، ويدعم مئات المواقع.

## المميزات
- لصق رابط، أو **مشاركة** الرابط من أي تطبيق (متصفح، فيسبوك، تيك توك...) إلى SnapLite.
- عرض العنوان والصورة المصغرة قبل التحميل.
- فيديو (Best / 1080p / 720p / 480p / 360p) أو صوت (MP3 / M4A).
- تحميل في الخلفية مع إشعار تقدّم وزر إلغاء.
- الحفظ في `Movies/SnapLite` و`Music/SnapLite` (يظهر في المعرض ومشغّل الموسيقى).
- زر **Update engine** لتحديث yt-dlp عندما يتغير موقع ما.

المتطلبات: أندرويد 10 أو أحدث.

## بناء الـ APK

### الطريقة أ: من الكمبيوتر (الأسهل)
1. ثبّت Android Studio.
2. `File > Open` واختر مجلد المشروع، وانتظر انتهاء Sync.
3. `Build > Build APK(s)`، ثم انقل الملف إلى هاتفك وثبّته.

### الطريقة ب: من الهاتف فقط (عبر GitHub Actions المجاني)
1. أنشئ حساباً في github.com ومستودعاً جديداً (مثلاً `snaplite`).
2. ارفع المشروع إلى المستودع. أسهل طريقة من الهاتف هي Termux:
   ```
   pkg install git unzip
   termux-setup-storage
   unzip ~/storage/downloads/SnapLite.zip -d ~
   cd ~/SnapLite
   git init && git add . && git commit -m "init"
   git branch -M main
   git remote add origin https://github.com/USERNAME/snaplite.git
   git push -u origin main
   ```
   (كلمة السر عند الرفع = Personal Access Token من إعدادات GitHub.)
3. افتح تبويب **Actions** > **Build APK** وانتظر دقائق.
4. نزّل الملف من **Artifacts** (`SnapLite-debug-apk`)، فُكّ الضغط، وثبّت الـ APK
   (فعّل "التثبيت من مصادر غير معروفة").

## ملاحظات
- المشروع لم يُترجم (يُبنى) داخل بيئة الإنشاء، فقد تظهر أخطاء صغيرة عند أول بناء. أرسلها لي وسأصلحها.
- تواريخ ونسخ المكتبات: yt-dlp-android 0.18.1، AGP 8.7.3، Kotlin 2.0.21.
- حمّل فقط محتوى تملكه أو مسموح لك بحفظه.
