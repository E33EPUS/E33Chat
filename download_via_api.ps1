$files = @(
    @{sha="11b7e3798fc20ba6700c8c61c1907469886e1079"; dest="ChatBubbleScreen.java"},
    @{sha="de800fb661ee690820f8cec09a3a0a1ccf576474"; dest="ChatMessageStore.java"},
    @{sha="3332fb91c4e2bf5cdc4414068599ce8b13e11cbc"; dest="ChatEmojiPanel.java"},
    @{sha="a7cc72d079837922f590bb471e9da507909a445c"; dest="ChatSettingsMenu.java"},
    @{sha="1cdb92cfc14063a157ba8891f266a0a3ee441afe"; dest="ChatSearchPanel.java"},
    @{sha="d55d4023baff116ebb0db21d8bf13a9fe7c2866e"; dest="ChatQuickChatPanel.java"},
    @{sha="f36736ab44c2992bc227196966eb4170cc9e846b"; dest="chat\notification\MentionNotificationController.java"},
    @{sha="549990345cc6970f92764ddbba7d19375ef5711f"; dest="chat\notification\MentionNotificationBanner.java"},
    @{sha="815e5830d3424c1a96697221d58a319b99fff68f"; dest="chat\MessagePresentation.java"},
    @{sha="1fa40a0b49a512562474398834d0bafca91a5a2a"; dest="chat\MentionDetector.java"},
    @{sha="486825b3bf8303f9c898b5753ffb5007a39d8e85"; dest="chat\TemplateMatcher.java"},
    @{sha="e2888facc4c9a0262e3e992650524d1a5c7feed9"; dest="ChatBubbleClientSetup.java"},
    @{sha="99ffe698fc0131e90ba38ab910616494b1d106b1"; dest="ChatBubbleMod.java"},
    @{sha="af8f5e53e161db2c6f8ea84f7d59d6b293eb6875"; dest="ChatBubbleHudOverlay.java"},
    @{sha="dccc450afd6f9c2070bd8cfc7b9b2e8563327479"; dest="ChatBubbleConfigScreen.java"},
    @{sha="f49082e7156323cd630c1d64a8e06e19eda132f9"; dest="ChatBubbleTheme.java"},
    @{sha="ee2d9ccfab353c91ee8f21b4fdd0ba82d047a2b1"; dest="Animation.java"},
    @{sha="910c8fe7e21282e42edd9db54a8da54c1a07da8a"; dest="BedScreen.java"},
    @{sha="11298a496775040bbc589f2574e3fb1a37ac0ecd"; dest="BlurRenderer.java"},
    @{sha="7ed185328f63f92da990d05cd99124a85db479e0"; dest="RoundRectRenderer.java"},
    @{sha="2970f470bb7b1cdf36c30b238407d53786c932c1"; dest="ServerConfigScreen.java"},
    @{sha="12053a1356e42284beaacaa9c7a38c4bef9a1376"; dest="UiLayout.java"}
)

$base = "https://api.github.com/repos/E33EPUS/E33Chat/git/blobs"
$root = "d:\MYCODE\E33Chat-Multi\common\src\main\java\com\niuqu\chatbubble"

[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = {$true}

$ok = 0
$fail = 0

foreach ($f in $files) {
    $url = "$base/$($f.sha)"
    $out = "$root\$($f.dest)"
    try {
        Write-Output "Downloading $($f.dest)..."
        $json = Invoke-RestMethod -Uri $url -ErrorAction Stop
        $content = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($json.content))
        Set-Content -Path $out -Value $content -Encoding UTF8 -NoNewline
        $size = (Get-Item $out).Length
        Write-Output "  OK ($size bytes)"
        $ok++
    } catch {
        Write-Output "  FAILED: $_"
        $fail++
    }
}

Write-Output "Done: $ok OK, $fail failed"