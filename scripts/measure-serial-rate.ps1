param(
  [string]$Port = "COM9",
  [int]$Baud = 115200,
  [int]$Seconds = 10
)
# Reads raw bytes from a serial port for a fixed window and reports the true
# byte rate plus how often a substring appears. Used to tell a real driver log
# flood from a serial-probe artifact, which cannot be judged from text alone.
$sp = New-Object System.IO.Ports.SerialPort $Port, $Baud
$sp.ReadTimeout = 500
$sp.WriteTimeout = 500
$sp.Open()
Start-Sleep -Milliseconds 300
$sp.DiscardInBuffer()

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$total = 0
$sb = New-Object System.Text.StringBuilder
$buf = New-Object byte[] 8192
while ($sw.Elapsed.TotalSeconds -lt $Seconds) {
  try {
    $n = $sp.Read($buf, 0, $buf.Length)
    if ($n -gt 0) {
      $total += $n
      [void]$sb.Append([System.Text.Encoding]::ASCII.GetString($buf, 0, $n))
    }
  } catch { }
}
$sp.Close()

$elapsed = $sw.Elapsed.TotalSeconds
$text = $sb.ToString()
$theoretical = $Baud / 10.0
Write-Output ("window            : {0:N1} s" -f $elapsed)
Write-Output ("bytes read        : {0}" -f $total)
Write-Output ("measured rate     : {0:N1} bytes/s" -f ($total / $elapsed))
Write-Output ("max possible rate : {0:N1} bytes/s at {1} baud" -f $theoretical, $Baud)
foreach ($needle in @("cannot set config", "ets Jul 29", "Attempting to reconnect", "ESP32 LoRa Receiver", "LoRa Receiver Active")) {
  $c = [regex]::Matches($text, [regex]::Escape($needle)).Count
  Write-Output ("occurrences '{0}' : {1}" -f $needle, $c)
}
$nonPrintable = ($text.ToCharArray() | Where-Object { [int]$_ -lt 32 -and $_ -notin @("`r", "`n", "`t") }).Count
Write-Output ("non-printable char: {0}" -f $nonPrintable)
