import type { SomeCompanionConfigField } from '@companion-module/base'

export type ModuleConfig = {
	host: string
	port: number
}

export type ModuleSecrets = {
	token: string
}

export function GetConfigFields(): SomeCompanionConfigField[] {
	return [
		{
			type: 'static-text',
			id: 'info',
			width: 12,
			label: 'PotPlayer Companion Remote',
			value: '輸入執行 PotPlayer Companion Bridge 的 Windows 主機位址、Port 與配對金鑰。',
		},
		{
			type: 'textinput',
			id: 'host',
			label: '主機位址',
			width: 7,
			default: '127.0.0.1',
			tooltip: '例如 192.168.1.50、playback-pc.local 或 IPv6 位址；不要輸入 ws:// 或 Port',
		},
		{
			type: 'number',
			id: 'port',
			label: '連接埠',
			width: 5,
			min: 1,
			max: 65535,
			default: 19191,
		},
		{
			type: 'secret-text',
			id: 'token',
			label: '配對金鑰',
			width: 12,
		},
	]
}
