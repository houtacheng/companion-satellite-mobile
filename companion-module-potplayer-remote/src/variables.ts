import type ModuleInstance from './main.js'

export type VariablesSchema = {
	connection_status: string
	media_folder: string
	media_file_count: number
	playback: string
	paused: boolean
	idle: boolean
	title: string
	url: string
	position: number
	duration: number
	remaining: number
	remaining_seconds: number
	playback_finished: boolean
	progress: number
	position_time: string
	duration_time: string
	remaining_time: string
	volume: number
	muted: boolean
	speed: number
	fullscreen: boolean
	pip: boolean
	ontop: boolean
	playlist_position: number
	playlist_count: number
	chapter: number
	chapter_count: number
	audio_track: number
	video_track: number
	subtitle_track: number
	second_subtitle_track: number
	audio_delay: number
	subtitle_delay: number
	subtitle_visible: boolean
	loop_file: string
	loop_playlist: string
	playback_mode: string
	shuffle: boolean
	filename: string
	video_info: string
	end_behavior: string
}

export function UpdateVariableDefinitions(self: ModuleInstance): void {
	self.setVariableDefinitions({
		connection_status: { name: '連線狀態' },
		media_folder: { name: '媒體資料夾' },
		media_file_count: { name: '媒體檔案數量' },
		playback: { name: '播放狀態' },
		paused: { name: '是否暫停' },
		idle: { name: '是否閒置' },
		title: { name: '媒體標題' },
		url: { name: '媒體路徑或網址' },
		position: { name: '目前時間（秒）' },
		duration: { name: '總長度（秒）' },
		remaining: { name: '剩餘時間（秒）' },
		remaining_seconds: { name: '剩餘整數秒數' },
		playback_finished: { name: '是否自然播放完畢' },
		progress: { name: '播放進度（百分比）' },
		position_time: { name: '格式化目前時間' },
		duration_time: { name: '格式化總長度' },
		remaining_time: { name: '格式化剩餘時間' },
		volume: { name: '音量' },
		muted: { name: '是否靜音' },
		speed: { name: '播放速度' },
		fullscreen: { name: '是否全螢幕' },
		pip: { name: '是否為子母畫面' },
		ontop: { name: '是否置頂' },
		playlist_position: { name: '播放清單位置' },
		playlist_count: { name: '播放清單項目數' },
		chapter: { name: '目前章節' },
		chapter_count: { name: '章節數量' },
		audio_track: { name: '音訊軌 ID' },
		video_track: { name: '視訊軌 ID' },
		subtitle_track: { name: '字幕軌 ID' },
		second_subtitle_track: { name: '第二字幕軌 ID' },
		audio_delay: { name: '音訊延遲' },
		subtitle_delay: { name: '字幕延遲' },
		subtitle_visible: { name: '字幕是否顯示' },
		loop_file: { name: '單檔循環狀態' },
		loop_playlist: { name: '播放清單循環狀態' },
		playback_mode: { name: '循環播放模式' },
		shuffle: { name: '是否隨機播放' },
		filename: { name: '目前檔名' },
		video_info: { name: '影片格式資訊' },
		end_behavior: { name: '播放完畢行為' },
	})
}
