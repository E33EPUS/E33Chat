$base = "https://raw.githubusercontent.com/E33EPUS/E33Chat/Fabric-1.21.1/src/main/java/com/niuqu/chatbubble"
$dest = "d:\MYCODE\E33Chat-Multi\common\src\main\java\com\niuqu\chatbubble"

$files = @(
    "ChatBubbleScreen.java",
    "ChatMessageStore.java",
    "MentionNotificationController.java",
    "ChatBubbleClientSetup.java",
    "ChatBubbleMod.java",
    "ChatEmojiPanel.java",
    "ChatSettingsMenu.java",
    "ChatSearchPanel.java",
    "ChatQuickChatPanel.java",
    "MessagePresentation.java",
    "MentionNotificationBanner.java",
    "ChatBubbleHudOverlay.java",
    "ChatBubbleConfigScreen.java",
    "ChatBubbleTheme.java",
    "Animation.java",
    "BedScreen.java",
    "BlurRenderer.java",
    "RoundRectRenderer.java",
    "ServerConfigScreen.java",
    "UiLayout.java"
)

[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = {$true}

$success = 0
$failed = 0

foreach ($f in $files) {
    $url = "$base/$f"
    $out = "$dest/$f"
    try {
        Write-Output "Downloading $f..."
        Invoke-WebRequest -Uri $url -OutFile $out -ErrorAction Stop
        $size = (Get-Item $out).Length
        Write-Output "  OK ($size bytes)"
        $success++
    } catch {
        Write-Output "  FAILED: $_"
        $failed++
    }
}

Write-Output "Done: $success OK, $failed failed"