# PotPlayer Bridge 0.2.0（Windows ARM64）

直接執行 PotPlayerCompanionBridge.exe，不需另外安裝 .NET。程式不開啟主視窗或主控台，圖示位於時鐘旁的系統匣；如果看不到，請展開隱藏圖示。

右鍵選單：複製 Token、查看 Log、開啟設定資料夾、結束。雙擊圖示也可開啟 Log。

設定與 Log：%LOCALAPPDATA%\PotPlayerCompanionBridge
- bridge-settings.json：連接埠、Token、媒體資料夾、播放器位置。
- bridge.log：啟動、連線與錯誤紀錄；超過 5 MB 時保留上一份 bridge.log.1。

首次執行時，若 EXE 旁有舊 bridge-settings.json，會匯入；已有使用者設定則沿用。修改設定後需結束並重啟程式。更新時先用系統匣選單結束舊版，再替換 EXE。0.1.1 主控台版需自行關閉以釋放 19191。

健康檢查：http://127.0.0.1:19191/
WebSocket：ws://127.0.0.1:19191/ws
與 Companion module 0.1.1 相容。

本版本已驗證：Windows ARM64 編譯、GUI 子系統（無主控台）、背景啟動、HTTP 0.2.0 回應、既有 Token WebSocket 認證、單一程序防護、埠衝突日誌。
右鍵選單的實際滑鼠操作、剪貼簿貼上與 Log 視窗仍需使用者確認。
