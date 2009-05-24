/*

	Tomato Firmware
	Copyright (C) 2006-2009 Jonathan Zarate

*/

#include "tomato.h"

#include <time.h>
#include <sys/stat.h>


void asp_ddnsx(int argc, char **argv)
{
	char *p, *q;
	int i;
	char s[256];
	char m[256];
	char name[64];
	time_t tt;
	struct stat st;

	switch (get_wan_proto()) {
	case WP_PPTP:
		p = "pptp_get_ip";
		break;
	case WP_L2TP:
		p = "l2tp_get_ip";
		break;
	default:
		p = "wan_ipaddr";
		break;
	}

	web_printf(
		"\nddnsx_ip = '%s';\n"
		"ddnsx_msg = [",
		nvram_safe_get(p));

	for (i = 0; i < 2; ++i) {
		web_puts(i ? "','" : "'");
		sprintf(name, "/var/lib/mdu/ddnsx%d.msg", i);
		f_read_string(name, m, sizeof(m));	// null term'd even on error
		if (m[0] != 0) {
			if ((stat(name, &st) == 0) && (st.st_mtime > Y2K)) {
				strftime(s, sizeof(s), "%a, %d %b %Y %H:%M:%S %z: ", localtime(&st.st_mtime));
				web_puts(s);
			}
			web_putj(m);
		}
	}

	web_puts("'];\nddnsx_last = [");

	for (i = 0; i < 2; ++i) {
		web_puts(i ? "','" : "'");
		sprintf(name, "ddnsx%d", i);
		if (!nvram_match(name, "")) {
			sprintf(name, "ddnsx%d_cache", i);
			if ((p = nvram_get(name)) == NULL) continue;
			tt = strtoul(p, &q, 10);
			if (*q++ != ',') continue;
			if (tt > Y2K) {
				strftime(s, sizeof(s), "%a, %d %b %Y %H:%M:%S %z: ", localtime(&tt));
				web_puts(s);
			}
			web_putj(q);
		}
	}
	web_puts("'];\n");
}

void asp_ddnsx_ip(int argc, char **argv)
{
	const char *p;

	switch (get_wan_proto()) {
	case WP_PPTP:
		p = "pptp_get_ip";
		break;
	case WP_L2TP:
		p = "l2tp_get_ip";
		break;
	default:
		p = "wan_ipaddr";
		break;
	}
	web_puts(nvram_safe_get(p));
}
