package com.qiuniu.servlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Avatar Servlet — returns user's photo or virtual assistant avatar as circular image.
 * GET /avatar.jsp?type=virtual   → virtual assistant avatar (SVG)
 * GET /avatar.jsp?type=user      → current user's photo
 * GET /avatar.jsp                → current user's photo (default)
 */
@WebServlet("/api/avatar/*")
public class AvatarServlet extends HttpServlet {

    private static final String AVATAR_BASE_PATH = "D:/qiuniu_install/avatar_base.jpg";
    private static final String VIRTUAL_AVATAR_SVG = buildVirtualAvatar();
    private static final Map<String, String> moodStore = new ConcurrentHashMap<>();

    private static String buildVirtualAvatar() {
        // Build a simple pixel-art style avatar SVG matching the user's photo characteristics
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"120\" height=\"120\" viewBox=\"0 0 120 120\">");
        sb.append("<defs>");
        sb.append("<clipPath id=\"c\"><circle cx=\"60\" cy=\"60\" r=\"58\"/></clipPath>");
        sb.append("<linearGradient id=\"skin\" x1=\"0%\" y1=\"0%\" x2=\"0%\" y2=\"100%\">");
        sb.append("<stop offset=\"0%\" stop-color=\"#fce4d6\"/><stop offset=\"100%\" stop-color=\"#f5d4c0\"/>");
        sb.append("</linearGradient>");
        sb.append("<linearGradient id=\"shirt\" x1=\"0%\" y1=\"0%\" x2=\"0%\" y2=\"100%\">");
        sb.append("<stop offset=\"0%\" stop-color=\"#4a6fa5\"/><stop offset=\"100%\" stop-color=\"#3d5a87\"/>");
        sb.append("</linearGradient>");
        sb.append("</defs>");

        // Background
        sb.append("<rect width=\"120\" height=\"120\" fill=\"#f0f2f8\"/>");

        // Clip group
        sb.append("<g clip-path=\"url(#c)\">");

        // Shirt / body (bottom half)
        sb.append("<rect x=\"20\" y=\"85\" width=\"80\" height=\"50\" fill=\"url(#shirt)\"/>");
        sb.append("<rect x=\"35\" y=\"82\" width=\"50\" height=\"10\" rx=\"3\" fill=\"#3d5a87\"/>");
        sb.append("<path d=\"M45 82 L60 95 L75 82\" fill=\"#f5f5f5\"/>");

        // Neck
        sb.append("<rect x=\"50\" y=\"75\" width=\"20\" height=\"12\" fill=\"url(#skin)\"/>");

        // Head
        sb.append("<ellipse cx=\"60\" cy=\"52\" rx=\"36\" ry=\"38\" fill=\"url(#skin)\"/>");

        // Ears
        sb.append("<ellipse cx=\"25\" cy=\"52\" rx=\"6\" ry=\"9\" fill=\"url(#skin)\"/>");
        sb.append("<ellipse cx=\"95\" cy=\"52\" rx=\"6\" ry=\"9\" fill=\"url(#skin)\"/>");

        // Hair (short black, based on user's photo)
        sb.append("<ellipse cx=\"60\" cy=\"22\" rx=\"30\" ry=\"16\" fill=\"#1a1a1a\"/>");
        sb.append("<path d=\"M30 35 Q40 22 60 18 Q80 22 90 35\" stroke=\"#1a1a1a\" stroke-width=\"10\" fill=\"none\" stroke-linecap=\"round\"/>");

        // Eyes
        sb.append("<ellipse cx=\"47\" cy=\"50\" rx=\"7\" ry=\"8\" fill=\"white\"/>");
        sb.append("<ellipse cx=\"73\" cy=\"50\" rx=\"7\" ry=\"8\" fill=\"white\"/>");
        sb.append("<circle cx=\"48\" cy=\"50\" r=\"4.5\" fill=\"#2c3e50\"/>");
        sb.append("<circle cx=\"74\" cy=\"50\" r=\"4.5\" fill=\"#2c3e50\"/>");
        sb.append("<circle cx=\"50\" cy=\"48\" r=\"1.8\" fill=\"white\"/>");
        sb.append("<circle cx=\"76\" cy=\"48\" r=\"1.8\" fill=\"white\"/>");

        // Eyebrows
        sb.append("<path d=\"M40 40 Q47 37 54 40\" stroke=\"#5d4e37\" stroke-width=\"2\" fill=\"none\" stroke-linecap=\"round\"/>");
        sb.append("<path d=\"M66 40 Q73 37 80 40\" stroke=\"#5d4e37\" stroke-width=\"2\" fill=\"none\" stroke-linecap=\"round\"/>");

        // Nose
        sb.append("<path d=\"M59 58 Q60 64 61 58\" stroke=\"#d4a574\" stroke-width=\"1.5\" fill=\"none\"/>");

        // Mouth (friendly smile)
        sb.append("<path d=\"M50 70 Q60 78 70 70\" stroke=\"#c0392b\" stroke-width=\"2.5\" fill=\"none\" stroke-linecap=\"round\"/>");

        // Blush
        sb.append("<ellipse cx=\"38\" cy=\"62\" rx=\"8\" ry=\"5\" fill=\"#ffb6c1\" opacity=\"0.4\"/>");
        sb.append("<ellipse cx=\"82\" cy=\"62\" rx=\"8\" ry=\"5\" fill=\"#ffb6c1\" opacity=\"0.4\"/>");

        sb.append("</g>"); // end clip

        // Border
        sb.append("<circle cx=\"60\" cy=\"60\" r=\"58\" fill=\"none\" stroke=\"#667eea\" stroke-width=\"3\"/>");

        sb.append("</svg>");
        return sb.toString();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String type = req.getParameter("type");
        boolean isVirtual = "virtual".equals(type);

        if (isVirtual) {
            // Return virtual assistant avatar as inline SVG
            resp.setContentType("image/svg+xml;charset=UTF-8");
            resp.setCharacterEncoding("UTF-8");
            resp.setHeader("Cache-Control", "no-cache");
            resp.getWriter().write(VIRTUAL_AVATAR_SVG);
        } else {
            // Return user's photo if exists, else placeholder
            File photo = new File(AVATAR_BASE_PATH);
            if (photo.exists()) {
                resp.setContentType("image/jpeg");
                resp.setHeader("Cache-Control", "max-age=86400");
                try (InputStream in = new FileInputStream(photo);
                     OutputStream out = resp.getOutputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }
            } else {
                // Return SVG placeholder
                resp.setContentType("image/svg+xml;charset=UTF-8");
                resp.setHeader("Cache-Control", "no-cache");
                resp.getWriter().write("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"120\" height=\"120\" viewBox=\"0 0 120 120\">" +
                    "<rect width=\"120\" height=\"120\" fill=\"#667eea\"/>" +
                    "<circle cx=\"60\" cy=\"45\" r=\"25\" fill=\"white\" opacity=\"0.9\"/>" +
                    "<ellipse cx=\"60\" cy=\"100\" rx=\"30\" ry=\"20\" fill=\"white\" opacity=\"0.7\"/>" +
                    "<text x=\"60\" y=\"75\" text-anchor=\"middle\" fill=\"white\" font-size=\"28\">👤</text>" +
                    "</svg>");
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.endsWith("/mood")) {
            String mood = req.getParameter("mood");
            if (mood != null && !mood.trim().isEmpty()) {
                moodStore.put(req.getSession().getId(), mood.trim());
            }
            String current = moodStore.getOrDefault(req.getSession().getId(), "default");
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"mood\":\"" + current + "\"}");
        } else {
            resp.setStatus(404);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"error\":\"unknown endpoint\"}");
        }
    }
}
