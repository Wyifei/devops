param($PackagePath)
Function Deploy{
Get-ChildItem $PackagePath|foreach-object {if ((Get-WebApplication -Name $_.Name).PhysicalPath) 
{
Copy-Item -Force -recurse $_.FullName -Destination (Get-Item (Get-WebApplication -Name $_.Name).PhysicalPath).Parent.FullName
Write-Output "Deploy package $_ to $((Get-WebApplication -Name $_.Name).PhysicalPath)"
}
else
{Write-Output "Package $_ doesn't have corresponding site,please take a check!"}}
}

Function DeployConfig{
Get-ChildItem $PackagePath|foreach-object {if ((Get-WebApplication -Name $_.Name).PhysicalPath) 
{
Copy-Item -Force -recurse $_.FullName -Destination (Get-Item (Get-WebApplication -Name $_.Name).PhysicalPath).Parent.FullName
Write-Output "Deploy config file of $_ to $((Get-WebApplication -Name $_.Name).PhysicalPath)"
}
else
{Write-Output "Config file $_ doesn't have corresponding site,please take a check!"}}
}

Function Backup{

$Destination_Path="C:\AutoDeploy\Backup\"+(Get-Date).ToString("yyyyMMdd")

if (!(Test-Path -Path $Destination_Path))
{
    New-Item -ItemType directory -Path $Destination_Path
}
Get-ChildItem $PackagePath|foreach-object {if ((Get-WebApplication -Name $_.Name).PhysicalPath)
{
Compress-ARCHIVE -Path (Get-WebApplication -Name $_.Name).PhysicalPath -DestinationPath $Destination_Path\$_.zip -Force
Write-Output "Back site $_ to $Destination_Path."
}
else
{Write-Output "Package $_ doesn't have corresponding site,please take a check!"}}
}

Function StopIIS{
Get-ChildItem $PackagePath|foreach-object {if ((Get-WebApplication -Name $_.Name).Applicationpool)
{
if ((Get-WebAppPoolState (Get-WebApplication -Name $_.Name).Applicationpool).Value -eq 'Started') 
{
Stop-WebAppPool (Get-WebApplication -Name $_.Name).Applicationpool
Write-Output "Stop IIS of $_."
}
}
else
{Write-Output "Package $_ doesn't have corresponding site,please take a check!"}
}
}

Function StartIIS{
Get-ChildItem $PackagePath|foreach-object {if ((Get-WebApplication -Name $_.Name).Applicationpool)
{
if ((Get-WebAppPoolState (Get-WebApplication -Name $_.Name).Applicationpool).Value -eq 'Stopped') 
{
Start-WebAppPool (Get-WebApplication -Name $_.Name).Applicationpool
Write-Output "Start IIS of $_."}
}
else
{Write-Output "Package $_ doesn't have corresponding site,please take a check!"}
}
}

Function Clean{
$Files = get-childitem $PackagePath -force
Foreach ($File in $Files)
{
$FilePath=$File.FullName
Remove-Item -Path $FilePath -Recurse -Force
}
}