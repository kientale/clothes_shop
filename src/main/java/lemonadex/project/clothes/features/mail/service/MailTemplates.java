package lemonadex.project.clothes.features.mail.service;

import org.springframework.web.util.HtmlUtils;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Small, inline-styled HTML emails that render in common mail clients. All values are escaped. */
public final class MailTemplates {
    private MailTemplates() {}

    public record Line(String name, String variant, int quantity, BigDecimal total) {}

    public static String passwordReset(String name, String link, long minutes) {
        return layout("Đặt lại mật khẩu", """
                <p>Chào %s,</p>
                <p>Bạn vừa yêu cầu đặt lại mật khẩu tài khoản LemonadeX. Bấm nút dưới đây để chọn mật khẩu mới.
                Liên kết dùng được một lần trong %d phút.</p>
                %s
                <p style="color:#626875;font-size:13px">Nếu bạn không yêu cầu, hãy bỏ qua email này; mật khẩu của bạn vẫn giữ nguyên.</p>
                """.formatted(esc(name), minutes, button(link, "Đặt lại mật khẩu")));
    }

    public static String emailVerification(String name, String link, long hours) {
        return layout("Xác nhận email", """
                <p>Chào %s,</p>
                <p>Cảm ơn bạn đã tạo tài khoản LemonadeX. Bấm nút dưới đây để xác nhận địa chỉ email này là của bạn.
                Liên kết dùng được một lần trong %d giờ.</p>
                %s
                <p style="color:#626875;font-size:13px">Nếu bạn không tạo tài khoản, hãy bỏ qua email này.</p>
                """.formatted(esc(name), hours, button(link, "Xác nhận email")));
    }

    public static String backInStock(String productName, String variant, String link) {
        return layout("Đã có hàng trở lại", """
                <p>Chào bạn,</p>
                <p><strong>%s</strong> (%s) mà bạn đăng ký nhận tin đã có hàng trở lại. Số lượng có hạn, bạn đặt sớm nhé.</p>
                %s
                <p style="color:#626875;font-size:13px">Bạn nhận email này vì đã bấm "Báo khi có hàng" trên LemonadeX. Mỗi đăng ký chỉ được báo một lần.</p>
                """.formatted(esc(productName), esc(variant), button(link, "Xem sản phẩm")));
    }

    public static String orderPlaced(String name, String orderCode, List<Line> lines, BigDecimal shipping, BigDecimal total,
                                     String address, String paymentNote, String link) {
        StringBuilder rows = new StringBuilder();
        for (Line line : lines) {
            rows.append("""
                    <tr><td style="padding:8px 0;border-bottom:1px solid #e6e8ec">%s<br><span style="color:#626875;font-size:13px">%s x %d</span></td>
                    <td style="padding:8px 0;border-bottom:1px solid #e6e8ec;text-align:right;white-space:nowrap">%s</td></tr>
                    """.formatted(esc(line.name()), esc(line.variant()), line.quantity(), money(line.total())));
        }
        return layout("Đã nhận đơn " + orderCode, """
                <p>Chào %s,</p>
                <p>LemonadeX đã nhận đơn <strong>%s</strong>. Cửa hàng sẽ xác nhận và giao hàng sớm.</p>
                <table style="width:100%%;border-collapse:collapse;font-size:14px">%s
                <tr><td style="padding:8px 0">Phí giao hàng</td><td style="padding:8px 0;text-align:right">%s</td></tr>
                <tr><td style="padding:8px 0;font-weight:600">Tổng cộng</td><td style="padding:8px 0;text-align:right;font-weight:600">%s</td></tr></table>
                <p><strong>Giao đến:</strong> %s</p>
                %s
                %s
                """.formatted(esc(name), esc(orderCode), rows, money(shipping), money(total), esc(address),
                paymentNote == null ? "" : "<p>" + esc(paymentNote) + "</p>", button(link, "Xem đơn hàng")));
    }

    private static String layout(String title, String body) {
        return """
                <!doctype html><html lang="vi"><body style="margin:0;background:#f4f4f4;font-family:Arial,Helvetica,sans-serif;color:#2a2e36">
                <div style="max-width:560px;margin:0 auto;padding:32px 16px">
                <p style="font-size:22px;font-weight:700;color:#0a0a0a;margin:0 0 24px">LemonadeX</p>
                <div style="background:#ffffff;border-radius:16px;padding:28px;line-height:1.6">
                <h1 style="font-size:20px;margin:0 0 16px;color:#0a0a0a">%s</h1>%s</div>
                <p style="color:#626875;font-size:12px;margin-top:16px">Email tự động từ LemonadeX, vui lòng không trả lời.</p>
                </div></body></html>
                """.formatted(esc(title), body);
    }

    private static String button(String link, String label) {
        return "<p style=\"margin:24px 0\"><a href=\"%s\" style=\"display:inline-block;background:#0a0a0a;color:#ffffff;text-decoration:none;padding:12px 24px;border-radius:999px;font-weight:600\">%s</a></p>"
                .formatted(esc(link), esc(label));
    }

    private static String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        return format.format(value == null ? BigDecimal.ZERO : value) + " ₫";
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
