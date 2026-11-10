# -*- coding: utf-8 -*-
"""Soạn / gửi mail 'Bảng giao việc tháng 11/2026' bằng Outlook classic (pywin32).
Mặc định: CHỈ XEM TRƯỚC (mail.Display(), không gửi). Gửi thật: python gui_mail_giao_viec.py --send
"""
import os, sys, html, datetime
import win32com.client

ATTACH = r"D:\ClaudeMail\output\Giao_viec_P_Ha_tang.xlsx"
USERS = ["buuth", "datnd5", "anhnha", "anhnan", "dungpt13",
         "habm1", "khietdn", "phuna1", "thaott_bte"]
TO = [u + "@viettel.com.vn" for u in USERS]
SUBJECT = "P.HT AGG – Bảng giao việc tháng 11/2026 (bản nháp, xin góp ý trước 12h ngày 11/11)"

BODY_TEXT = """Dear all,

Anh gửi file Giao_viec_P_Ha_tang.xlsx: bảng giao việc tháng 11/2026 theo QĐ 3223/QĐ-VTNet. Đây là BẢN NHÁP, anh nhờ mọi người xem phần việc của mình và báo chỗ nào chưa đúng trước 12h ngày mai (11/11).

CÁCH XEM
1. Mở file, vào sheet mang tên mình (Bửu, Đạt, Duy Anh, Ninh Anh, Dung, Hà, Khiết, Phú, Thảo). Cộng tác viên xem sheet CTV của mình.
2. Mục I là việc chính, mục II là việc hỗ trợ. Mỗi việc có kết quả cần đạt, hạn, mức ưu tiên (Gấp/Thường).
3. Sheet Tổng hợp cho thấy số việc mở và việc cảnh báo của từng người.

NẾU CÓ CHỖ CHƯA ĐÚNG
Ví dụ: sai người phụ trách, sai hạn, việc trùng, thiếu việc, việc không phải của mình.
- Ghi ngay vào cột Ghi chú của dòng đó ở sheet của mình, ghi rõ: sai gì, đề nghị sửa thế nào.
- Việc thiếu: ghi vào dòng cuối sheet của mình, nêu tên việc, kết quả cần đạt và hạn.
- Mọi người không sửa cột Số (cột A), không xóa cả dòng và không sửa sheet Bảng giao việc, vì sẽ làm sai công thức. Anh sẽ sửa tập trung sau khi tổng hợp góp ý.

Cảm ơn cả phòng."""

def body_html():
    paras = []
    for p in BODY_TEXT.split("\n\n"):
        paras.append("<p style='margin:0 0 12px 0'>" + html.escape(p).replace("\n", "<br>") + "</p>")
    return ("<html><body><div style=\"font-family:'Times New Roman',serif;font-size:13pt\">"
            + "".join(paras) + "</div></body></html>")

def main():
    send = "--send" in sys.argv
    if not os.path.isfile(ATTACH):
        sys.exit("DỪNG: không thấy file đính kèm: " + ATTACH)
    mt = datetime.datetime.fromtimestamp(os.path.getmtime(ATTACH))
    print("File:", ATTACH, "| sửa lần cuối:", mt.strftime("%d/%m/%Y %H:%M"))
    if mt.date() != datetime.date(2026, 11, 10):
        print("CẢNH BÁO: ngày sửa file khác 10/11/2026 - kiểm tra đúng bản mới nhất.")

    ol = win32com.client.Dispatch("Outlook.Application")
    mail = ol.CreateItem(0)          # thư MỚI
    mail.Subject = SUBJECT
    for a in TO:
        mail.Recipients.Add(a)
    ok = mail.Recipients.ResolveAll()
    bad = [r.Address for r in mail.Recipients if not r.Resolved]
    if not ok or bad:
        mail.Close(1)                # 1 = olDiscard
        sys.exit("DỪNG: địa chỉ không resolve được: " + ", ".join(bad or ["(ResolveAll=False)"]))
    names = [r.Name for r in mail.Recipients]
    mail.HTMLBody = body_html()      # không chữ ký
    mail.Attachments.Add(ATTACH)

    print("Người nhận (đã resolve):")
    for i, n in enumerate(names, 1):
        print(f"  {i}. {n}")
    if send:
        mail.Send()
        print("ĐÃ GỬI lúc", datetime.datetime.now().strftime("%H:%M:%S %d/%m/%Y"),
              "| đính kèm:", os.path.basename(ATTACH))
    else:
        mail.Display()
        print("CHƯA GỬI - thư đang mở để xem trước. Gửi thật: thêm --send")

if __name__ == "__main__":
    main()
