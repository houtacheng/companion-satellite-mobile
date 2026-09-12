import type ModuleInstance from './main.js'

export type ActionsSchema = {
	play_media_file: { options: { path: string; startFromBeginning: boolean } }
	refresh_library: { options: Record<string, never> }
	transport: { options: { operation: string } }
	seek_relative: { options: { seconds: number; exact: boolean } }
	seek_absolute: { options: { seconds: number } }
	set_position_percent: { options: { percent: number } }
	set_volume: { options: { volume: number } }
	change_volume: { options: { amount: number } }
	mute: { options: { operation: string } }
	set_speed: { options: { speed: number } }
	change_speed: { options: { amount: number } }
	reset_speed: { options: Record<string, never> }
	playlist: { options: { operation: string } }
	fullscreen: { options: { operation: string } }
	fullscreen_on_screen: { options: { screen: string } }
	ontop: { options: { enabled: boolean } }
	toggle_ontop: { options: Record<string, never> }
	open: { options: { url: string } }
	playback_tools: { options: { operation: string; screenshot_mode: string } }
	auto_close_on_end: { options: { enabled: boolean } }
	playlist_manage: { options: { operation: string; url: string } }
	ab_loop: { options: { operation: string } }
	subtitle_visibility: { options: { enabled: boolean } }
	loop: { options: { mode: string } }
	set_rotation: { options: { degrees: string } }
	set_aspect: { options: { aspect: string } }
	video_adjustment: { options: { property: string; amount: number } }
	window_control: { options: { operation: string } }
}

export function UpdateActions(self: ModuleInstance): void {
	self.setActionDefinitions({
		play_media_file: {
			name: '從媒體資料夾選擇檔案播放',
			options: [
				{
					id: 'path',
					type: 'dropdown',
					label: '媒體檔案',
					default: self.mediaFiles[0]?.path || '',
					choices: self.mediaFiles.length
						? self.mediaFiles.map((f) => ({ id: f.path, label: f.label }))
						: [{ id: '', label: '尚未取得媒體清單，請先更新' }],
				},
				{ id: 'startFromBeginning', type: 'checkbox', label: '從頭播放', default: true },
			],
			callback: async (event) => {
				const path = String(event.options.path || '')
				if (path) self.sendCommand('open', { url: path, startFromBeginning: event.options.startFromBeginning === true })
			},
		},
		refresh_library: { name: '更新媒體檔案清單', options: [], callback: async () => self.refreshLibrary() },
		transport: {
			name: '播放控制',
			options: [
				{
					id: 'operation',
					type: 'dropdown',
					label: '操作',
					default: 'toggle_play_pause',
					choices: [
						{ id: 'play', label: '播放' },
						{ id: 'pause', label: '暫停' },
						{ id: 'toggle_play_pause', label: '播放／暫停切換' },
						{ id: 'stop', label: '停止' },
					],
				},
			],
			callback: async (event) => self.sendCommand(String(event.options.operation)),
		},
		seek_relative: {
			name: '相對跳轉',
			options: [
				{ id: 'seconds', type: 'number', label: '秒數（負數為後退）', default: 10, min: -86400, max: 86400 },
				{ id: 'exact', type: 'checkbox', label: '精確跳轉', default: true },
			],
			callback: async (event) => self.sendCommand('seek_relative', { seconds: event.options.seconds }),
		},
		seek_absolute: {
			name: '跳至指定時間',
			options: [{ id: 'seconds', type: 'number', label: '從開頭起算秒數', default: 0, min: 0, max: 864000 }],
			callback: async (event) => self.sendCommand('seek_absolute', { seconds: event.options.seconds }),
		},
		set_position_percent: {
			name: '跳至播放百分比',
			options: [{ id: 'percent', type: 'number', label: '百分比', default: 50, min: 0, max: 100 }],
			callback: async (event) => self.sendCommand('set_position_percent', { percent: event.options.percent }),
		},
		set_volume: {
			name: '設定音量',
			options: [{ id: 'volume', type: 'number', label: '音量', default: 50, min: 0, max: 100 }],
			callback: async (event) => self.sendCommand('set_volume', { volume: event.options.volume }),
		},
		change_volume: {
			name: '相對調整音量',
			options: [{ id: 'amount', type: 'number', label: '變化量（負數為降低）', default: 5, min: -100, max: 100 }],
			callback: async (event) => self.sendCommand('change_volume', { amount: event.options.amount }),
		},
		mute: {
			name: '靜音控制',
			options: [
				{
					id: 'operation',
					type: 'dropdown',
					label: '操作',
					default: 'toggle',
					choices: [
						{ id: 'on', label: '開啟靜音' },
						{ id: 'off', label: '關閉靜音' },
						{ id: 'toggle', label: '切換靜音' },
					],
				},
			],
			callback: async (event) => {
				const op = String(event.options.operation)
				self.sendCommand(op === 'toggle' ? 'toggle_mute' : 'set_mute', op === 'toggle' ? {} : { muted: op === 'on' })
			},
		},
		set_speed: {
			name: '設定播放速度',
			options: [{ id: 'speed', type: 'number', label: '倍速', default: 1, min: 0.2, max: 12, step: 0.05 }],
			callback: async (event) => self.sendCommand('set_speed', { speed: event.options.speed }),
		},
		change_speed: {
			name: '相對調整播放速度',
			options: [{ id: 'amount', type: 'number', label: '倍速變化量', default: 0.1, min: -11.8, max: 11.8, step: 0.05 }],
			callback: async (event) => self.sendCommand('change_speed', { amount: event.options.amount }),
		},
		reset_speed: { name: '重設播放速度', options: [], callback: async () => self.sendCommand('reset_speed') },
		playlist: {
			name: '播放清單控制',
			options: [
				{
					id: 'operation',
					type: 'dropdown',
					label: '操作',
					default: 'playlist_next',
					choices: [
						{ id: 'playlist_previous', label: '上一個' },
						{ id: 'playlist_next', label: '下一個' },
					],
				},
			],
			callback: async (event) => self.sendCommand(String(event.options.operation)),
		},
		fullscreen: {
			name: '全螢幕控制',
			options: [
				{
					id: 'operation',
					type: 'dropdown',
					label: '操作',
					default: 'toggle',
					choices: [
						{ id: 'on', label: '開啟' },
						{ id: 'off', label: '關閉' },
						{ id: 'toggle', label: '切換' },
					],
				},
			],
			callback: async (event) => {
				const op = String(event.options.operation)
				self.sendCommand(
					op === 'toggle' ? 'toggle_fullscreen' : 'set_fullscreen',
					op === 'toggle' ? {} : { enabled: op === 'on' },
				)
			},
		},
		fullscreen_on_screen: {
			name: '在指定螢幕全螢幕播放',
			options: [
				{
					id: 'screen',
					type: 'dropdown',
					label: '目標螢幕',
					default: '2',
					choices: [1, 2, 3, 4].map((screen) => ({ id: String(screen), label: `螢幕 ${screen}` })),
				},
			],
			callback: async (event) => self.sendCommand('fullscreen_on_screen', { screen: Number(event.options.screen) }),
		},
		ontop: {
			name: '視窗置頂',
			options: [{ id: 'enabled', type: 'checkbox', label: '保持在最上層', default: true }],
			callback: async (event) => self.sendCommand('set_ontop', { enabled: event.options.enabled }),
		},
		toggle_ontop: { name: '切換視窗置頂', options: [], callback: async () => self.sendCommand('toggle_ontop') },
		open: {
			name: '開啟媒體',
			options: [{ id: 'url', type: 'textinput', label: 'Windows 檔案路徑或網址', default: '', useVariables: true }],
			callback: async (event) => self.sendCommand('open', { url: event.options.url }),
		},
		playback_tools: {
			name: '其他播放操作',
			options: [
				{
					id: 'operation',
					type: 'dropdown',
					label: '操作',
					default: 'frame_step',
					choices: [
						{ id: 'frame_step', label: '前進一格' },
						{ id: 'frame_back_step', label: '後退一格' },
						{ id: 'screenshot', label: '截圖' },
						{ id: 'close_window', label: '關閉播放器' },
					],
				},
				{
					id: 'screenshot_mode',
					type: 'dropdown',
					label: '截圖內容',
					default: 'current',
					choices: [{ id: 'current', label: '依 PotPlayer 目前設定' }],
				},
			],
			callback: async (event) => self.sendCommand(String(event.options.operation)),
		},
		auto_close_on_end: {
			name: '播放完畢自動關閉視窗',
			options: [{ id: 'enabled', type: 'checkbox', label: '啟用', default: true }],
			callback: async (event) => self.sendCommand('set_auto_close_on_end', { enabled: event.options.enabled }),
		},
		playlist_manage: {
			name: '播放清單管理',
			options: [
				{
					id: 'operation',
					type: 'dropdown',
					label: '操作',
					default: 'playlist_add',
					choices: [
						{ id: 'playlist_add', label: '加入項目' },
						{ id: 'playlist_clear', label: '清除播放清單' },
						{ id: 'playlist_shuffle', label: '切換隨機播放' },
					],
				},
				{ id: 'url', type: 'textinput', label: '加入的 Windows 路徑或網址', default: '', useVariables: true },
			],
			callback: async (event) => self.sendCommand(String(event.options.operation), { url: event.options.url }),
		},
		ab_loop: {
			name: '設定／切換 AB 循環點',
			options: [
				{
					id: 'operation',
					type: 'dropdown',
					label: '操作',
					default: 'next',
					choices: [
						{ id: 'next', label: '依序設定 A 點／B 點／取消' },
						{ id: 'cancel', label: '取消 AB 循環' },
					],
				},
			],
			callback: async (event) => self.sendCommand('ab_loop', { operation: event.options.operation }),
		},
		subtitle_visibility: {
			name: '字幕顯示',
			options: [{ id: 'enabled', type: 'checkbox', label: '顯示字幕', default: true }],
			callback: async (event) => self.sendCommand('set_subtitle_visibility', { enabled: event.options.enabled }),
		},
		loop: {
			name: '循環播放設定',
			options: [
				{
					id: 'mode',
					type: 'dropdown',
					label: '播放模式',
					default: 'auto_next',
					choices: [
						{ id: 'no_repeat', label: '不循環（不自動下一首）' },
						{ id: 'repeat_one', label: '單曲循環' },
						{ id: 'auto_next', label: '自動下一首（清單結束不循環）' },
						{ id: 'repeat_playlist', label: '清單循環' },
						{ id: 'shuffle', label: '隨機播放' },
					],
				},
			],
			callback: async (event) => {
				const legacyTarget = (event.options as Record<string, unknown>).target
				const mode =
					event.options.mode === 'toggle'
						? legacyTarget === 'playlist'
							? 'repeat_playlist'
							: 'repeat_one'
						: event.options.mode
				self.sendCommand('set_playback_mode', { mode })
			},
		},
		set_rotation: {
			name: '旋轉影片',
			options: [
				{
					id: 'degrees',
					type: 'dropdown',
					label: '角度',
					default: '0',
					choices: ['0', '90', '180', '270'].map((degrees) => ({ id: degrees, label: `${degrees}°` })),
				},
			],
			callback: async (event) => self.sendCommand('set_rotation', { degrees: Number(event.options.degrees) }),
		},
		set_aspect: {
			name: '設定畫面比例',
			options: [
				{
					id: 'aspect',
					type: 'dropdown',
					label: '比例',
					default: 'original',
					choices: [
						{ id: 'original', label: '原始比例' },
						{ id: '4:3', label: '4:3' },
						{ id: '16:9', label: '16:9' },
						{ id: '16:10', label: '16:10' },
						{ id: '1.85:1', label: '1.85:1' },
						{ id: '2.35:1', label: '2.35:1' },
					],
				},
			],
			callback: async (event) => self.sendCommand('set_aspect', { aspect: event.options.aspect }),
		},
		video_adjustment: {
			name: '調整影片影像',
			options: [
				{
					id: 'property',
					type: 'dropdown',
					label: '項目',
					default: 'brightness',
					choices: [
						{ id: 'brightness', label: '亮度' },
						{ id: 'contrast', label: '對比' },
						{ id: 'saturation', label: '飽和度' },
						{ id: 'hue', label: '色相' },
					],
				},
				{ id: 'amount', type: 'number', label: '相對調整量（負數為降低）', default: 1, min: -100, max: 100 },
			],
			callback: async (event) =>
				self.sendCommand('adjust_video', { property: event.options.property, amount: event.options.amount }),
		},
		window_control: {
			name: '播放器視窗控制',
			options: [
				{
					id: 'operation',
					type: 'dropdown',
					label: '操作',
					default: 'minimize',
					choices: [
						{ id: 'minimize', label: '縮到最小' },
						{ id: 'restore', label: '還原視窗' },
						{ id: 'toggle_playlist', label: '開啟／關閉播放清單視窗' },
					],
				},
			],
			callback: async (event) => self.sendCommand('window_control', { operation: event.options.operation }),
		},
	})
}
