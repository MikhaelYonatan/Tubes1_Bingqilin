Tugas Besar 1 IF2211 Strategi Algoritma - Battlecode 2025 

## Author (Identitas Pembuat)

* Nathan Adhika Santosa - 13524041
* Ahmad Zaky Robbani - 13524045
* Mikhael Andrian Yonatan - 13524051

## Penjelasan Singkat Algoritma Greedy

### Bot Utama

Bot ini mengimplementasikan strategi navigasi berbasis heuristik simetri peta dan pergerakan greedy yang dilengkapi teknik wall-following untuk menjamin mobilitas robot menuju markas lawan tanpa terjebak hambatan fisik. Efisiensi sumber daya menjadi prioritas utama melalui mekanisme pengecatan selektif oleh Soldier dan Splasher guna menghindari pemborosan cat pada wilayah sekutu, sementara Tower membatasi agresi hanya pada musuh sekarat demi menjaga fokus utama pada produksi unit. Sistem produksi tersebut bersifat adaptif terhadap dinamika lapangan, di mana Tower akan beralih dari memproduksi Soldier ke unit Splasher atau Mopper berdasarkan tingkat kepadatan musuh dan intensitas warna lawan di sekitar. Melengkapi ekosistem ini, Mopper bertindak sebagai unit pendukung multifungsi yang secara cerdas memprioritaskan serangan ayun dan pembersihan teritori, sekaligus berperan sebagai distributor logistik cat bagi sekutu dengan tetap mengutamakan prinsip pertahanan diri.
### Bot Alternatif 1

Strategi ini berfokus pada penguasaan wilayah secara agresif dengan memanfaatkan heuristik simetri peta untuk mengarahkan robot menjelajahi area tengah dan lokasi strategis lainnya melalui sistem peluang navigasi yang terukur. Tower menerapkan kebijakan produksi berbasis probabilitas yang sangat memprioritaskan unit Splasher (72%) untuk ekspansi wilayah secara masif, didukung oleh sistem pertahanan menara yang aktif menghambat pergerakan lawan. Efisiensi sumber daya dicapai melalui mekanisme serangan area Splasher dan pengecatan presisi Soldier yang hanya menargetkan petak non-sekutu, serta peran strategis Mopper dalam melumpuhkan cadangan cat musuh. Selain itu, aspek keberlanjutan unit dijamin melalui fungsi pengisian ulang cat (refill) otomatis saat cadangan kritis dan sistem upgrade menara secara mandiri, yang secara keseluruhan mempercepat dominasi peta dan memperkuat manajemen sumber daya tim.

### Bot Alternatif 2

Strategi ini menerapkan pendekatan greedy yang terspesialisasi untuk setiap unit dengan fokus utama pada penguasaan sumber daya melalui sistem navigasi canggih berbasis evaluasi skor prioritas dan memori anti-looping (25 posisi terakhir). Operasi dimulai dengan pembagian wilayah eksplorasi berdasarkan ID robot (depan, kanan, kiri), di mana Splasher bertugas sebagai eksploitasi AoE dan penanda ruin, Soldier sebagai unit konstruksi pola tower dan penghancur struktur lawan, serta Mopper yang menjalankan peran tripartit sebagai penjaga, pembersih, dan distributor logistik cat. Keberlangsungan ekonomi diatur secara lokal oleh setiap tower dengan prioritas upgrade (Money > Defense > Paint) yang didahulukan daripada produksi unit melalui manajemen ambang batas kas, sementara efisiensi koordinasi tim diperkuat oleh jaringan komunikasi antar-robot yang saling berbagi data lokasi ruin, tower sekutu, dan posisi musuh secara real-time.

## Requirement Program dan Instalasi

Untuk dapat menjalankan dan melakukan build pada program ini, pastikan Anda telah menginstal beberapa perangkat lunak berikut:

* *Gradle: Sistem *build yang digunakan dalam proyek ini. Pastikan Anda telah menginstal Gradle di sistem Anda.


* 
*Java Development Kit (JDK)*: Diperlukan untuk menjalankan dan melakukan kompilasi kode bahasa Java.


* *Git: (Opsional) Digunakan untuk melakukan *clone repository.

## Command atau Langkah-langkah Meng-compile Program

Berikut adalah instruksi untuk melakukan kompilasi dan menjalankan game engine / bot:

1. Buka terminal atau command prompt.
2. Lakukan clone repository dengan menjalankan perintah berikut:


bash
git clone https://github.com/Fariz36/STIMA-battle




3. Pindah ke dalam direktori proyek:


bash
cd STIMA-battle




4. Lakukan proses build menggunakan Gradle:


bash
./gradlew build




5. Pindah ke direktori aplikasi client:


bash
cd client




6. Jalankan aplikasi client yang telah di-build.


7. Saat aplikasi berjalan, akan ada opsi untuk memilih direktori permainan. Pastikan Anda memilih direktori STIMA-battle sebagai directory root, bukan memilih direktori STIMA-battle/src.
