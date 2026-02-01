export default function Signin() {
  return (
    <div className="bg-amber-400">
      <div>
        <div>
          <h1>ログイン</h1>
          <p>GitTixにログイン</p>
        </div>

        <form>
          <div>
            <span>E-Mail</span>
            <input type="text" placeholder="you@example.com" />
          </div>

          <div>
            <span>Password</span>
            <input type="text" placeholder="*********" />
          </div>

          <button>ログイン</button>
        </form>
      </div>
    </div>
  )
}
