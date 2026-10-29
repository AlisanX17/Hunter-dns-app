# Hunter DNS Installer for Android

این پروژه نسخه اندرویدیِ اولیه برای مدیریت نصب پنل Hunter DNS روی VPS از طریق SSH است. برای ساخت APK، فایل `.github/workflows/build.yml` به GitHub Actions اضافه شده است.

## قابلیت‌ها
- اتصال SSH با IP/hostname، پورت، نام کاربری و رمز عبور
- تست اتصال SSH
- انتخاب نقش Relay / Exit / Single / Node
- تنظیم دامنه پنل، پروفایل و transport
- اجرای check، version، tunnel و uninstall
- نمایش خروجی فرمان‌ها در برنامه
- ساخت APK از طریق GitHub Actions

## نصب در GitHub
1. تمام محتویات این پوشه را در ریشه مخزن GitHub آپلود و Commit کنید.
2. تب Actions را باز کنید.
3. workflow با نام `Build Hunter DNS Android Installer` را اجرا کنید.
4. پس از موفقیت، Artifact با نام `HunterDNS-Android-Installer-APK` را از صفحه اجرای workflow دانلود کنید.

## مهم
- برنامه دستور نصب را از `https://raw.githubusercontent.com/AlisanX17/Hunter-dns/main/hunter-dns.sh` دریافت می‌کند. اگر آدرس مخزن یا نام فایل نصب شما تغییر کرده، آن را در `MainActivity.kt` اصلاح کنید.
- برای اجرای فرمان‌ها، کاربر SSH باید مجوز کافی داشته باشد؛ ساده‌ترین حالت کاربر root است.
- نسخه فعلی برای راحتی، بررسی کلید میزبان SSH را غیرفعال کرده است؛ این برای انتشار عمومی امن نیست. پیش از استفاده جدی باید تأیید اثرانگشت کلید میزبان پیاده‌سازی شود.
- گزینه‌های محیطی INSTALL_ROLE، TRANSPORT، PROFILE، ENABLE_BBR و DISABLE_AUTO_UPDATE باید در نسخه فعلی اسکریپت نصب شما پشتیبانی شوند؛ اگر اسکریپت از آن‌ها پشتیبانی نکند، انتخاب‌ها روی نصب اثر نمی‌گذارند. پیش از اجرای نصب، اسکریپت را بررسی کنید.
- «Update installer» در این نسخه وضعیت اسکریپت را بررسی می‌کند؛ بروزرسانی واقعیِ سرویس‌های نصب‌شده ممکن است نیازمند گزینه‌ای در خود اسکریپت باشد.
- رمز SSH فقط در حافظه همان اجرای برنامه نگه داشته می‌شود و در فایل ذخیره نمی‌شود.
