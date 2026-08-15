# MeshiHQApp HANDOFF

## これは何
近所の飲食店を自分のDBに貯めて管理するAndroidアプリ。オントロジー「飲食店マップ管理アプリ」の実装第一弾。

## 現在地点
v1.3.5（公式サイト取込・ホットペッパー取込まで）。AI検索／おすすめ／通知は未着手。
バージョンはタグ系列に合わせている（v0.6 → v1.3.4 で合流）。

## 取込元と信頼度
公式サイト official 0.9 ／ ホットペッパー hotpepper 0.8 ／ 写真OCR ocr 0.7 ／ OSM osm 0.6。手入力 self_visit 1.0 が最強。
- `ImportCandidate` が取込元によらない共通型。`shop.osm_id` を外部IDとして使い回す（OSMは `node/123`、ホットペッパーは `hp/J001234567`）
- `ShopRepository.importCandidates()` が新規追加／自動更新／承認待ちの振り分けを一手に引き受ける
- `applyExternalValues()` は公式サイトなど1店舗ぶんの反映で、同じ判定を通る
- ホットペッパーはクレジット表記が必須。取込画面に「Powered by ホットペッパー グルメ」を出している。店舗情報そのものの再販は規約で禁止
- 公式サイトは1店ずつ手動実行のみ。一括クロールはしない

## パッケージ名について
applicationId は `jp.appathy.meshihq2`（namespace は `jp.appathy.meshihq` のまま）。
`jp.appathy.meshihq` は端末側に残骸があるらしくインストールが通らなかったため、恒久的にこちらを使う。戻さないこと。

## 決定事項
- カテゴリは固定16種（和食／中華／寿司／焼肉／しゃぶしゃぶ／イタリアン／ピザ専門／ファミレス／ラーメン／たこ焼き／うどん／ハンバーガー／アイス／カフェ／居酒屋／その他）。`domain/Model.kt` の `Categories.ALL` が唯一の定義元
- 予算は「1人あたり1000〜3000円、500円刻みの帯」。DBには `budget_*_min` / `budget_*_max` を持ち、詳細画面の人数チップ（1〜6人）で合計を表示する
- エクスポートはBonsaiのRAG資料形式（`type: shop` の frontmatter 付きMarkdown）＋ バックアップ用 `shops.json`。書き出し先はSAFで選んだフォルダ
- BONSAI_API.md は読むだけ。サーバ側の仕様変更はこのチャットからは出さない

## 構成
- Kotlin / Jetpack Compose / Room / osmdroid（APIキー不要）
- 位置情報は標準 LocationManager（Google Play services 非依存）
- v0.1 ではViewModelを置かず、画面Composable内で状態を持つ。画面が太ってきた時点で分離する

## 主要ファイル
- `domain/Model.kt` … カテゴリ・予算・信頼度・距離計算
- `data/db/Entities.kt` … shop / fact_source / pending_change
- `data/repo/ShopRepository.kt` … 保存時に fact_source へ出所を記録（手入力は self_visit 1.0）
- `data/export/BonsaiExport.kt` … Markdown資料とJSONの書き出し・取り込み
- `ui/` … home / map / detail / edit / settings

## v0.2で入ったもの
- `data/remote/OverpassClient.kt` … Overpass APIへPOST。amenity=restaurant/cafe/fast_food/pub/bar/ice_cream かつ name 付きのみ取得
- `OsmCategory` … OSMの cuisine / amenity から固定16カテゴリへ写像
- `domain/OpeningHours.kt` … OSMの opening_hours を内部JSONへ変換（Mo-Fr 形式、複数スパン、off に対応）。失敗時は null を返し原文を raw に退避
- `ShopRepository.importFromOsm()` … 新規追加／自動更新／承認待ちの振り分け。既存店の同定は osm_id、無ければ同名かつ80m以内
- `ui/import_/ImportScreen.kt` … 取込タブ。半径選択、取込実行、承認待ちの承認・却下

### 更新判定の実装
値ごとに `fact_source` の最良信頼度を引き、OSMの0.6がそれ以上なら自動更新。下回れば `pending_change` に積む。
既存値が空のときは無条件で埋める。自分で入力した値（1.0）は必ず承認待ち経由になる。

## v0.3で入ったもの
- DBスキーマ v2。`photo` と `menu_item` を追加。`MIGRATION_1_2` を手書きで用意しているので既存データは残る
- `data/media/PhotoStore.kt` … ギャラリーで選んだ画像を長辺1600pxに縮小してアプリ内へコピー。URI権限切れを避けるため実体を持つ
- `data/ocr/MenuOcr.kt` … ML Kit 日本語モデル（端末内完結、通信なし）で行を読み、`〜円` や `¥〜` から価格を、残りを品名として候補化
- 店舗詳細のタブが 情報／メニュー／写真／根拠 の4枚に
- Bonsai資料のMarkdownに「## メニュー」節を追加。RAG側で品名と価格が引ける

### 出所種別に ocr を追加
信頼度0.7。手入力（1.0）より下、OSM（0.6）より上。

## v0.4で入ったもの
- DBスキーマ v3。`visit` / `collection` / `collection_shop` を追加（`MIGRATION_2_3` あり）
- 店舗詳細に来店タブ。日付は記録時点、人数・支払い・評価1〜5・メモ。合計と1人あたり平均を表示
- 来店を記録すると、状態が「未確認」の店は自動で「営業中」に上がる
- 記録タブ（ボトムナビ5枚目）… カテゴリ別／月別来店／よく行く店／コレクション管理
- コレクションへの登録は店舗詳細の情報タブのチップから
- メニュー追加時に同名かつ同価格を弾き、「重複を整理」で同名を1件へ畳む（価格入りを優先して残す）

## v0.5で入ったもの
- DBスキーマ v4。`photo.taken_at` を追加（`MIGRATION_3_4`）
- 写真を取り込むとき、縮小コピーを作る前に元画像のEXIFから撮影日時を読む（androidx.exifinterface）
- 写真カードの「来店に」でその撮影日の来店記録を作る（人数1・メモ「写真の撮影日から記録」）
- 来店記録の日付を今日／昨日／2日前／3日前から選べる
- 記録タブに期間フィルタ（今月／今年／全期間）。来店回数・支払い合計・月別・よく行く店に効く
- 取込は前回実行から60秒以内なら実行せず案内を出す（Overpassは公共サーバのため）

## v1.3.5で入ったもの
- DBスキーマ v5（`shop.website` を追加、MIGRATION_4_5）
- `HotPepperClient` … グルメサーチAPI。キーワード検索の結果を「店名に含む」「住所に含む」でさらに絞る。
  緯度経度がAPIから返るのでジオコーディング不要。APIキーは設定画面に保存（端末内のみ）
- `OfficialSiteClient` … 公式サイトを1ページ取得し、schema.org の JSON-LD があればそこから、
  無ければ本文の「営業時間」「〒住所」「TEL」「地図リンクの q=lat,lng」を拾う
- 店舗詳細の情報タブに公式サイトURL欄と「サイトから取得」。読み取り結果はチェックで選んで反映
- 取込画面に取込元の切替（OSM 範囲 / ホットペッパー 条件）

## v0.6で入ったもの
- ホームにカテゴリ／コレクションの絞り込みチップ。コレクションは `collection_shop` を全件購読してローカルで突き合わせる
- 地図にカテゴリ絞り込み。ピンは300件を上限とし、超えたら地図中心から近い順に間引く
- deploy.sh に `git pull --rebase origin main` を追加。CatalogApp の rollout.sh がGitHub API経由で
  release.yml と ci/appathy.keystore を直接コミットするため、pullなしのpushはrejectされる

## 次にやること（v0.7）
1. AI検索・おすすめ（BonsaiAppクライアント）。着手前に BONSAI_API.md をこのリポジトリに置くこと。
   契約が手元にない状態でエンドポイントを推測して実装しない
2. 通知。何を通知するかが未定（候補: しばらく行っていないお気に入り／承認待ちの滞留／営業時間内で近くにいるときの提案）
3. 承認待ちが0件のときの取込タブの見せ方（今は空リストが出るだけ）

## deploy.sh（恒久仕様）
push とタグ発行までを1コマンドで完結させる形に固定。shebang は Termux のフルパス、`set -e` は付けない。
`git pull --rebase origin main` は必須（CatalogApp が API 経由で release.yml と ci/appathy.keystore を
直接コミットするため、無いと push が rejected になる）。
最新リリースのタグ末尾を +1 したタグを GitHub API で打ち、Actions のビルドと自作アプリストアへの反映につなげる。
`ci/` と `.github/workflows/release.yml` は削除しないこと。ZIPには同梱していないが、pull で降りてきたものを維持する。

## ビルドの2レーン（v1.3.4以降）
- **配布**: タグ push → release.yml が `assembleRelease` → `apksigner` で `ci/appathy.keystore`
  （pass: appathy-store / alias: appathy）に署名し直して Release を作る。
  したがって release ビルドに signingConfig は設定しない（未署名で出し、Actions側で署名する）
- **手元確認**: 通常 push → build.yml が `assembleDebug`。`keystore/meshihq.jks` で署名し、
  applicationId に `.debug` を付けるので配布版と共存できる（データは別）
- versionName はタグ系列に合わせる。次のタグは v1.3.4 の想定

## 旧ビルド・署名メモ
push すると GitHub Actions が release APK（未minify）を Artifacts に出す。

署名鍵は `keystore/meshihq.jks` をリポジトリに同梱し、debug/release とも同じ鍵で署名する（storePassword/keyPassword/alias すべて `meshihq`）。
CIランナーの debug.keystore は実行ごとに作り直されるため、それに任せると毎回署名が変わり、上書きインストールが
「INSTALL_FAILED_UPDATE_INCOMPATIBLE（署名が一致しません）」で失敗する。鍵を固定することでこれを回避している。
配布用の鍵ではないので、ストア公開する段階になったら別の鍵に差し替えること。
