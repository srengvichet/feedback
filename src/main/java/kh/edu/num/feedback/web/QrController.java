package kh.edu.num.feedback.web;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.OutputStream;

@Controller
@RequestMapping("/qr")
public class QrController {

  @Value("${app.base-url:}")
  private String configuredBaseUrl;

  @GetMapping(value="/join/{code}.png")
  public void joinQr(@PathVariable String code, HttpServletResponse resp) throws Exception {
    // Use configured base URL (LAN IP) if set, otherwise fall back to request-derived URL
    String base = (configuredBaseUrl != null && !configuredBaseUrl.isBlank())
        ? configuredBaseUrl
        : ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();

    String url = base + "/student/join/" + code;

    var matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 240, 240);

    resp.setContentType("image/png");
    try (OutputStream os = resp.getOutputStream()) {
      MatrixToImageWriter.writeToStream(matrix, "PNG", os);
    }
  }
}
