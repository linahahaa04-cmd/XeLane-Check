$ErrorActionPreference = "Stop"
$root = "C:\Users\doanh\Desktop\AABrowser\app\src\main\java\com\zunguwu\XeLane"
New-Item -ItemType Directory -Force -Path "$root\data", "$root\settings", "$root\web" | Out-Null

$bpUrl = "https://raw.githubusercontent.com/kododake/AABrowser/main/app/src/main/java/com/kododake/aabrowser/data/BrowserPreferences.kt"
$svUrl = "https://raw.githubusercontent.com/kododake/AABrowser/main/app/src/main/java/com/kododake/aabrowser/settings/SettingsViews.kt"
$wvUrl = "https://raw.githubusercontent.com/kododake/AABrowser/main/app/src/main/java/com/kododake/aabrowser/web/ConfiguredWebView.kt"
$sbUrl = "https://raw.githubusercontent.com/kododake/AABrowser/main/app/src/main/java/com/kododake/aabrowser/web/SpeechRecognitionBridge.kt"

$bp = (Invoke-WebRequest -UseBasicParsing -Uri $bpUrl).Content
$bp = $bp -replace "package com\.kododake\.aabrowser\.data", "package com.zunguwu.XeLane.data"
$bp = $bp -replace "com\.kododake\.aabrowser\.model", "com.zunguwu.XeLane.model"
[IO.File]::WriteAllText("$root\data\BrowserPreferences.kt", $bp, [Text.UTF8Encoding]::new($false))

$sv = (Invoke-WebRequest -UseBasicParsing -Uri $svUrl).Content
$sv = $sv -replace "package com\.kododake\.aabrowser\.settings", "package com.zunguwu.XeLane.settings"
$sv = $sv -replace "com\.kododake\.aabrowser\.", "com.zunguwu.XeLane."
[IO.File]::WriteAllText("$root\settings\SettingsViews.kt", $sv, [Text.UTF8Encoding]::new($false))

$wv = (Invoke-WebRequest -UseBasicParsing -Uri $wvUrl).Content
$wv = $wv -replace "package com\.kododake\.aabrowser\.web", "package com.zunguwu.XeLane.web"
$wv = $wv -replace "com\.kododake\.aabrowser\.", "com.zunguwu.XeLane."
[IO.File]::WriteAllText("$root\web\ConfiguredWebView.kt", $wv, [Text.UTF8Encoding]::new($false))

$sb = (Invoke-WebRequest -UseBasicParsing -Uri $sbUrl).Content
$sb = $sb -replace "package com\.kododake\.aabrowser\.web", "package com.zunguwu.XeLane.web"
$sb = $sb -replace "com\.kododake\.aabrowser\.", "com.zunguwu.XeLane."
[IO.File]::WriteAllText("$root\web\SpeechRecognitionBridge.kt", $sb, [Text.UTF8Encoding]::new($false))

Write-Output "Wrote BrowserPreferences.kt, SettingsViews.kt, and web sources"
