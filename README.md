# Card Scanner with Google Sheet (Android)

Scan visiting cards (front and back) with the camera or gallery. Details (Name, Designation, Organisation, Phone, Email, Website, Address) are read automatically, shown in a list you can edit, exported as CSV and optionally added to a Google Sheet.

## 1. Build the APK on GitHub
1. Upload ALL files of this folder to the ROOT of a GitHub repository (including the hidden .github folder).
2. Open the Actions tab. "Build APK" runs by itself (or click Run workflow).
3. When green, open the run and download "app-debug-apk" from Artifacts. The APK is inside.

## 2. Set up Google Sheet (optional)
1. Create a Google Sheet. Extensions > Apps Script.
2. Paste the code from AppsScript.gs. Change "change-me" to your own secret word.
3. Deploy > New deployment > type Web app. Execute as: Me. Who has access: Anyone. Deploy and approve permissions.
4. Copy the Web app URL.

## 3. Use the app
1. Settings: paste the API key, the Web app URL and your secret word.
2. Scan with camera / From gallery. Every card read is saved on the phone and sent to the sheet.
3. "Load from Sheet" replaces the list on the phone with the rows from the sheet.
Edits made in the app are only on the phone, not written back to the sheet.
